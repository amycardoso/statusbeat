package com.statusbeat.statusbeat.constants;

public final class AppConstants {

    private AppConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Routes
    public static final String HOME_PATH = "/";
    public static final String SUCCESS_PATH = "/success";
    public static final String ERROR_PATH = "/error";

    // OAuth paths
    public static final String OAUTH_SPOTIFY_PATH = "/oauth/spotify";
    public static final String OAUTH_SPOTIFY_CALLBACK_PATH = "/oauth/spotify/callback";
    public static final String SLACK_INSTALL_PATH = "/slack/install";

    // Error message query params
    public static final String ERROR_PARAM_INVALID_USER = "invalid_user_id";
    public static final String ERROR_PARAM_SPOTIFY_DENIED = "spotify_auth_denied";
    public static final String ERROR_PARAM_SPOTIFY_ERROR = "spotify_auth_error";

    // Error message keys
    public static final String ERROR_USER_NOT_FOUND = "User not found";
    public static final String ERROR_USER_SETTINGS_NOT_FOUND = "User settings not found";
    public static final String ERROR_FAILED_TO_UPDATE_SLACK_STATUS = "Failed to update Slack status";
    public static final String ERROR_FAILED_TO_CLEAR_SLACK_STATUS = "Failed to clear Slack status";
    public static final String ERROR_FAILED_TO_SEND_SLACK_MESSAGE = "Failed to send Slack message";

    // Spotify
    public static final String SPOTIFY_GREEN_COLOR = "#1DB954";
    public static final String SPOTIFY_USER_ID_PREFIX = "spotify_user_";
    public static final String UNKNOWN_ARTIST = "Unknown Artist";

    // Template placeholders
    public static final String PLACEHOLDER_EMOJI = "{emoji}";
    public static final String PLACEHOLDER_TITLE = "{title}";
    public static final String PLACEHOLDER_ARTIST = "{artist}";
}
