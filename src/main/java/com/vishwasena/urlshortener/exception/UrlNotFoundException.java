package com.vishwasena.urlshortener.exception;

public class UrlNotFoundException extends UrlShortenerException {
    public UrlNotFoundException(String shortCode) {
        super("Short URL not found: " + shortCode);
    }
}
