package com.robspecs.streaming.security;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.robspecs.streaming.exceptions.JWTBlackListedTokenException;
import com.robspecs.streaming.service.TokenBlacklistService;
import com.robspecs.streaming.utils.JWTUtils;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JWTRefreshFilter extends OncePerRequestFilter {

	private final AuthenticationManager authenticationManager;
	private final JWTUtils jwtUtil;
	private final CustomUserDetailsService customUserDetailsService;
	private final TokenBlacklistService tokenService;
	private final static Logger logger = LoggerFactory.getLogger(JWTRefreshFilter.class);

	public JWTRefreshFilter(AuthenticationManager authenticationManager, JWTUtils jwtUtil,
			CustomUserDetailsService customUserDetailsService, TokenBlacklistService tokenService) {
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.customUserDetailsService = customUserDetailsService;
		this.tokenService = tokenService;
		logger.info("JWTRefreshFilter initialized.");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		logger.info("Entering JWTRefreshFilter for request URI: {}", request.getRequestURI());

		String path = request.getRequestURI();
		boolean isRefreshRequest = path.equals("/api/auth/refresh");

		// *** HIGHLIGHTED CHANGE START ***
		// This filter now ONLY processes requests to /api/auth/refresh.
		// It no longer performs implicit refreshes for other paths based on expired
		// access tokens.
		if (!isRefreshRequest) {
			logger.info("Request path {} is not /api/auth/refresh. Skipping JWTRefreshFilter logic.", path);
			filterChain.doFilter(request, response);
			return;
		}
		// *** HIGHLIGHTED CHANGE END ***

		// --- Main Refresh Logic (Only for /api/auth/refresh endpoint) ---
		logger.info("Processing explicit refresh token request for path: {}", path);
		try {
			// This filter now directly looks for the refresh token in cookies
			// as it's handling an explicit refresh request from the frontend.
			String refreshToken = extractJwtFromRequest(request);
			if (refreshToken == null) {
				logger.warn(
						"Refresh token is null or not present in cookies for explicit refresh request. Throwing BadCredentialsException.");
				request.setAttribute("custom-error", "Refresh Token is invalid or not present.");
				request.setAttribute("custom-exception", "MissingRefreshTokenException");
				throw new BadCredentialsException("Refresh token is required.");
			}
			logger.debug("Refresh token extracted from cookie (first 10 chars): {}",
					refreshToken.substring(0, Math.min(refreshToken.length(), 10)) + "...");

			if (tokenService.isBlacklisted(refreshToken)) {
				logger.warn("Refresh token is blacklisted: (first 10 chars) {}. Throwing JWTBlackListedTokenException.",
						refreshToken.substring(0, Math.min(refreshToken.length(), 10)) + "...");
				request.setAttribute("custom-error", "Refresh Token Blacklisted. Please log in again.");
				request.setAttribute("custom-exception", JWTBlackListedTokenException.class.getName());
				throw new JWTBlackListedTokenException("Refresh token is Blacklisted");
			}
			logger.debug("Refresh token is not blacklisted.");

			String usernameFromRefreshToken = jwtUtil.validateAndExtractUsername(refreshToken);
			logger.info("Username extracted from refresh token: {}", usernameFromRefreshToken);

			UserDetails userDetails = customUserDetailsService.loadUserByUsername(usernameFromRefreshToken);
			if (!userDetails.isEnabled()) {
				logger.warn("Refresh token owner {} profile is not enabled. Throwing exception.",
						userDetails.getUsername());
				throw new BadCredentialsException("User profile not enabled.");
			}
			logger.debug("User details loaded and enabled for refresh: {}", userDetails.getUsername());

			String newAccessToken = jwtUtil.generateToken(userDetails.getUsername(), 15); // 15 min expiration for
																							// access token
			logger.info("New Access Token generated for user: {}.", userDetails.getUsername());

			response.setHeader("Authorization", "Bearer " + newAccessToken);
			response.setStatus(HttpServletResponse.SC_OK); // Explicitly set 200 OK
			response.setContentType("application/json");
			response.getWriter().write("{\"message\": \"Access token refreshed successfully\"}");
			logger.info("New Access Token sent in Authorization header for user: {}.", userDetails.getUsername());
            return;


		} catch (ExpiredJwtException e) {
			logger.warn("Explicit refresh token expired for user: {}. Message: {}", e.getClaims().getSubject(),
					e.getMessage());
			request.setAttribute("custom-error", "Refresh Token Expired. Please log in again.");
			request.setAttribute("custom-exception", e.getClass().getName());
			throw new BadCredentialsException("Refresh token expired");
		} catch (JWTBlackListedTokenException e) {
			logger.warn("Explicit refresh token processing failed: Blacklisted token. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Refresh Token Blacklisted. Please log in again.");
			request.setAttribute("custom-exception", e.getClass().getName());
			throw new BadCredentialsException("Refresh token blacklisted");
		} catch (UsernameNotFoundException e) {
			logger.warn("Explicit refresh token processing failed: Username from refresh token not found. Message: {}",
					e.getMessage());
			request.setAttribute("custom-error", "Invalid Refresh Token. User not found.");
			request.setAttribute("custom-exception", e.getClass().getName());
			throw new BadCredentialsException("Refresh token invalid: user not found");
		} catch (Exception e) {
			logger.error("Explicit refresh token processing failed due to unexpected error for request URI {}: {}",
					request.getRequestURI(), e.getMessage(), e);
			request.setAttribute("custom-error", "Refresh token failure due to internal error.");
			request.setAttribute("custom-exception", e.getClass().getName());
			throw new BadCredentialsException("Refresh token failure");
		}
	}

	private String extractJwtFromRequest(HttpServletRequest request) {
		logger.debug("Attempting to extract refresh token from cookies for explicit refresh.");
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			logger.debug("No cookies found in the request for refresh token.");
			return null;
		}

		return Arrays.stream(cookies).filter(cookie -> "refreshToken".equals(cookie.getName())).map(Cookie::getValue)
				.findFirst().orElseGet(() -> {
					logger.debug("Refresh token cookie not found by name 'refreshToken'.");
					return null;
				});
	}
}
