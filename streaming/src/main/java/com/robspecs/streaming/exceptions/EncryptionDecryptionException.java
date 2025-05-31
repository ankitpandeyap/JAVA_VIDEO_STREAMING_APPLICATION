package com.robspecs.streaming.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class EncryptionDecryptionException extends RuntimeException {
    /**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public EncryptionDecryptionException(String message) {
        super(message);
    }

    public EncryptionDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
