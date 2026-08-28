package com.vishwasena.urlshortener.exception;

public class ExpiredUrlException extends UrlShortenerException {
    public ExpiredUrlException(String shortCode) {
        super("Short URL has expired: " + shortCode);
    }
}
