package com.vishwasena.urlshortener.exception;

public class UrlShortenerException extends RuntimeException {
    public UrlShortenerException(String message) {
        super(message);
    }

    public UrlShortenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
