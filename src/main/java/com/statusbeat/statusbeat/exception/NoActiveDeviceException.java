package com.statusbeat.statusbeat.exception;

public class NoActiveDeviceException extends SpotifyException {
    public NoActiveDeviceException() {
        super("No active Spotify device found");
    }

    public NoActiveDeviceException(String message) {
        super(message);
    }
}
