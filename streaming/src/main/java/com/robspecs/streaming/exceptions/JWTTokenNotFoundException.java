package com.robspecs.streaming.exceptions;

import org.springframework.security.core.AuthenticationException;

public class JWTTokenNotFoundException extends AuthenticationException {

	public JWTTokenNotFoundException(String msg) {
		super(msg);
		// TODO Auto-generated constructor stub
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 515498247974825464L;



}