package com.robspecs.streaming.exceptions;

import org.springframework.security.core.AuthenticationException;

public class JWTBlackListedTokenException extends AuthenticationException {

	public JWTBlackListedTokenException(String msg) {
		super(msg);
		// TODO Auto-generated constructor stub
	}

}
