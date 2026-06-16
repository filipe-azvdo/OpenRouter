package com.personalrouter.exception;

public class OpenRouteServiceUnavailableException extends OpenRouteServiceException {

    public OpenRouteServiceUnavailableException(String message) {
        super(message);
    }

    public OpenRouteServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
