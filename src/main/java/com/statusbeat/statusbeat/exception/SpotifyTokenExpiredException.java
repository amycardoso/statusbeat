package com.statusbeat.statusbeat.exception;

public class SpotifyTokenExpiredException extends SpotifyException {
    public SpotifyTokenExpiredException() {
        super("Spotify access token expired");
    }

    public SpotifyTokenExpiredException(String message) {
        super(message);
    }
}
