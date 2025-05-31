package com.robspecs.streaming.utils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Component
public class JWTUtils {
    private final static Logger logger = LoggerFactory.getLogger(JWTUtils.class);
    private final String SECRET_KEY ;
    private final Key key ;

    public JWTUtils(@Value("${jwt.secret}") String secretKeyString) { // Constructor injection
        this. SECRET_KEY = secretKeyString;
        this.key = Keys.hmacShaKeyFor(this. SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        logger.info("JWTUtils initialized.");
    }

    // Generate JWT Token
    public String generateToken(String username, long expiryMinutes) {
        logger.info("Generating JWT token for username: {} with expiry: {} minutes", username, expiryMinutes);
        try {
            String token = Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000)) //in milliseconds
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
            logger.info("Successfully generated JWT token for username: {}", username);
            return token;
        } catch (Exception e) {
        	  logger.error("Error generating JWT token for user: {}. Error: {}", username, e.getMessage(), e);
            return null; // Or throw an exception, depending on your error handling policy
        }
    }

    public String validateAndExtractUsername(String token) {
        logger.info("Validating and extracting username from token: {}", token);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String username = claims.getSubject();
            logger.debug("Username extracted from token: {}", username);
            return username;
        } catch (MalformedJwtException m) {
            logger.error("MalformedJwtException: Invalid JWT token format. Token: {}  Error: {}", token, m.getMessage());
           throw m;
        } catch (ExpiredJwtException e) {
            logger.error("ExpiredJwtException: JWT token has expired. Token: {}  Error: {}", token, e.getMessage());
            throw e;
        } catch (SignatureException s) {
            logger.error("SignatureException: JWT token signature is invalid. Token: {} Error: {}", token, s.getMessage());
            throw s;
        } catch (IllegalArgumentException i) {
            logger.error("IllegalArgumentException: JWT token is invalid. Token: {} Error: {}", token, i.getMessage());
            throw i;
        } catch (Exception e) {
            logger.error("Exception:  Error validating/extracting from token {}. Error: {}", token, e.getMessage());
            throw e;
        }
    }
}
