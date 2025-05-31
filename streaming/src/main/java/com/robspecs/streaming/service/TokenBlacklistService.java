package com.robspecs.streaming.service;

public interface TokenBlacklistService {

	Boolean isBlacklisted(String token);

	void blacklistToken(String token , long timeInHours);
}
