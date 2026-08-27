package com.vishwasena.urlshortener.exception;

public class DisabledUrlException extends UrlShortenerException {
    public DisabledUrlException(String shortCode) {
        super("Short URL is disabled: " + shortCode);
    }
}
