package com.robspecs.streaming.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Default HTTP status if not caught specifically
public class VideoRetrievalException extends RuntimeException {

    public VideoRetrievalException(String message) {
        super(message);
    }

    public VideoRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
