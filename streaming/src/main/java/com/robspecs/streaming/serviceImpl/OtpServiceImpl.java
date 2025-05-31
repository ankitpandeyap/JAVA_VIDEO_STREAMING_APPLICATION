package com.robspecs.streaming.serviceImpl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.robspecs.streaming.exceptions.EncryptionDecryptionException;
import com.robspecs.streaming.exceptions.InvalidOtpException;
import com.robspecs.streaming.exceptions.OtpCooldownException;
import com.robspecs.streaming.exceptions.TooManyOtpAttemptsException;
import com.robspecs.streaming.service.OtpService;



@Service
public class OtpServiceImpl implements OtpService {
    private static SecureRandom secureRandom;
    private final StringRedisTemplate redisTemplate;
    private static final long OTP_EXPIRATION_MINUTES = 5;
    private static final long COOLDOWN_SECONDS = 20;
    private static final int MAX_ATTEMPTS = 5;
    private static final long VERIFICATION_FLAG_EXPIRATION_MINUTES = 10;
    private static final Logger logger = LoggerFactory.getLogger(OtpServiceImpl.class);

    static {
        secureRandom = new SecureRandom();
        logger.debug("SecureRandom instance initialized");
    }

    public OtpServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        logger.debug("OtpServiceImpl initialized");
    }

    @Override
    public String generateOtp(String email) {
        logger.info("Generating OTP for email: {}", email);
        String cooldownKey = email + ":cooldown";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            logger.warn("OTP request blocked due to cooldown for email: {}", email);
            // Changed to OtpCooldownException
            throw new OtpCooldownException("Please wait " + COOLDOWN_SECONDS + " seconds before requesting a new OTP.");
        }

        String otp = generateSecureOtp();
        String encryptedOtp = encryptOtp(otp);

        redisTemplate.opsForValue().set(email, encryptedOtp, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        logger.debug("Encrypted OTP stored in Redis for email: {}", email);

        redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        logger.debug("Cooldown set for email: {}", email);

        String attemptsKey = email + ":attempts";
        redisTemplate.delete(attemptsKey);
        logger.debug("Attempts counter reset for email: {}", email);

        String verifiedFlagKey = email + ":verified";
        redisTemplate.delete(verifiedFlagKey);
        logger.debug("Verification flag deleted for email: {}", email);

        logger.info("OTP generated and stored for email: {}", email);
        return otp;
    }

    @Override
    public boolean validateOtp(String email, String otp) {
        logger.info("Validating OTP for email: {}", email);
        String encryptedStoredOtp = redisTemplate.opsForValue().get(email);

        if (encryptedStoredOtp == null) {
            logger.warn("Invalid or expired OTP for email: {}", email);
            // Changed to InvalidOtpException
            throw new InvalidOtpException("Invalid or expired OTP.");
        }

        String attemptsKey = email + ":attempts";
        Integer attempts = Optional.ofNullable(redisTemplate.opsForValue().get(attemptsKey))
                                   .map(Integer::valueOf)
                                   .orElse(0);
        logger.debug("Current attempts for email {}: {}", email, attempts);

        if (attempts >= MAX_ATTEMPTS) {
            logger.warn("Too many failed attempts for email: {}", email);
            // Changed to TooManyOtpAttemptsException
            throw new TooManyOtpAttemptsException("Too many failed attempts. Please try again later.");
        }

        String encryptedEnteredOtp = encryptOtp(otp);

        if (encryptedStoredOtp.equals(encryptedEnteredOtp)) {
            logger.info("OTP is correct for email: {}", email);
            redisTemplate.delete(email);
            logger.debug("OTP deleted from Redis for email: {}", email);
            redisTemplate.delete(attemptsKey);
            logger.debug("Attempts counter deleted from Redis for email: {}", email);

            String verifiedFlagKey = email + ":verified";
            redisTemplate.opsForValue().set(verifiedFlagKey, "true", VERIFICATION_FLAG_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            logger.debug("Verification flag set for email: {}", email);

            return true;
        } else {
            logger.warn("Invalid OTP provided for email: {}", email);
            redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            logger.debug("Incorrect OTP, attempts incremented for email: {}", email);
            // Changed to InvalidOtpException
            throw new InvalidOtpException("Invalid OTP.");
        }
    }

    private String generateSecureOtp() {
        int otpNumber = secureRandom.nextInt(1_000_000);
        String otp = String.format("%06d", otpNumber);
        logger.debug("Generated OTP: {}", otp);
        return otp;
    }

    private String encryptOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            String encryptedOtp = Base64.getEncoder().encodeToString(hash);
            logger.debug("OTP encrypted");
            return encryptedOtp;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to encrypt OTP: {}", e.getMessage());
            // Changed to EncryptionDecryptionException
            throw new EncryptionDecryptionException("Failed to encrypt OTP due to missing algorithm", e);
        }
    }


}