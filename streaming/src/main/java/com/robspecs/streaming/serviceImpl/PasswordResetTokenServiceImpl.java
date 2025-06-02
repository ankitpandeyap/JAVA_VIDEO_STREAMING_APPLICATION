package com.robspecs.streaming.serviceImpl;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.robspecs.streaming.service.PasswordResetTokenService;

@Service
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

	private static final String RESET_TOKEN_PREFIX = "password_reset:";
	// Token valid for 15 minutes. This value is used in the email template.
	public static final Long TOKEN_EXPIRATION_MINUTES = 15L; // Made public to be accessible by MailService

	private static final Logger logger = LoggerFactory.getLogger(PasswordResetTokenService.class);

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Override
	public String generateAndStoreToken(String userEmail) {
		String token = UUID.randomUUID().toString();
		String key = RESET_TOKEN_PREFIX + token;
		// Store the user's email as the value for the token key
		redisTemplate.opsForValue().set(key, userEmail, TOKEN_EXPIRATION_MINUTES, TimeUnit.MINUTES);
		logger.info("Generated token for email {} with key {} and expiration {} minutes.", userEmail, key,
				TOKEN_EXPIRATION_MINUTES);
		return token;
	}

	@Override
	public String validateToken(String token) {
		String key = RESET_TOKEN_PREFIX + token;
		String userEmail = redisTemplate.opsForValue().get(key);
		if (userEmail != null) {
			logger.info("Token {} is valid for user: {}", token, userEmail);
		} else {
			logger.warn("Token {} is invalid or expired.", token);
		}
		return userEmail; // Returns null if token doesn't exist or expired
	}

	@Override
	public void invalidateToken(String token) {
		String key = RESET_TOKEN_PREFIX + token;
		Boolean deleted = redisTemplate.delete(key);
		if (Boolean.TRUE.equals(deleted)) {
			logger.info("Token {} invalidated successfully.", token);
		} else {
			logger.warn("Token {} not found or already invalidated.", token);
		}
	}

}
