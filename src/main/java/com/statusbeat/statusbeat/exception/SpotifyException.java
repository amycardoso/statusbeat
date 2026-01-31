package com.statusbeat.statusbeat.exception;

public class SpotifyException extends RuntimeException {
    public SpotifyException(String message) {
        super(message);
    }

    public SpotifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
