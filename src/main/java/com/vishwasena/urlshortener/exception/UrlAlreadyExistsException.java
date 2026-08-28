package com.vishwasena.urlshortener.exception;

public class UrlAlreadyExistsException extends RuntimeException {
    public UrlAlreadyExistsException(String message) {
        super(message);
    }

    public UrlAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
