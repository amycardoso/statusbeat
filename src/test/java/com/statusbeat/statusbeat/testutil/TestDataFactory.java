package com.statusbeat.statusbeat.testutil;

import com.statusbeat.statusbeat.model.CurrentlyPlayingTrackInfo;
import com.statusbeat.statusbeat.model.OAuthState;
import com.statusbeat.statusbeat.model.SyncContentType;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static User createUser() {
        return createUser("U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public static User createUser(String slackUserId) {
        return User.builder()
                .id(UUID.randomUUID().toString())
                .slackUserId(slackUserId)
                .slackTeamId("T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .encryptedSlackAccessToken("encrypted-slack-access-token")
                .encryptedSlackBotToken("encrypted-slack-bot-token")
                .active(true)
                .statusCleared(true)
                .manualStatusSet(false)
                .tokenInvalidated(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static User createUserWithSpotify() {
        User user = createUser();
        user.setSpotifyUserId("spotify-user-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEncryptedSpotifyAccessToken("encrypted-access-token");
        user.setEncryptedSpotifyRefreshToken("encrypted-refresh-token");
        user.setSpotifyTokenExpiresAt(LocalDateTime.now().plusHours(1));
        return user;
    }

    public static User createUserWithExpiredSpotifyToken() {
        User user = createUserWithSpotify();
        user.setSpotifyTokenExpiresAt(LocalDateTime.now().minusMinutes(10));
        return user;
    }

    public static User createUserWithCurrentlyPlaying() {
        User user = createUserWithSpotify();
        user.setCurrentlyPlayingSongId("track-id-123");
        user.setCurrentlyPlayingSongTitle("Test Song");
        user.setCurrentlyPlayingArtist("Test Artist");
        user.setLastSyncedAt(LocalDateTime.now());
        user.setLastSetStatusText("Test Song - Test Artist");
        user.setStatusCleared(false);
        return user;
    }

    public static User createInvalidatedUser() {
        User user = createUserWithSpotify();
        user.setTokenInvalidated(true);
        user.setTokenInvalidatedAt(LocalDateTime.now());
        user.setActive(false);
        return user;
    }

    public static User createUserWithManualStatus() {
        User user = createUserWithSpotify();
        user.setManualStatusSet(true);
        return user;
    }

    public static UserSettings createUserSettings(String userId) {
        return UserSettings.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .syncEnabled(true)
                .syncActive(false)
                .defaultEmoji(":musical_note:")
                .notificationsEnabled(false)
                .showArtist(true)
                .showSongTitle(true)
                .statusTemplate("{artist} - {title}")
                .workingHoursEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static UserSettings createUserSettingsWithSyncActive(String userId) {
        UserSettings settings = createUserSettings(userId);
        settings.setSyncActive(true);
        return settings;
    }

    public static UserSettings createUserSettingsWithWorkingHours(String userId) {
        UserSettings settings = createUserSettings(userId);
        settings.setWorkingHoursEnabled(true);
        settings.setSyncStartHour(900);  // 09:00 UTC
        settings.setSyncEndHour(1700);   // 17:00 UTC
        settings.setTimezoneOffsetSeconds(0); // UTC
        return settings;
    }

    public static UserSettings createUserSettingsWithDeviceFilter(String userId, List<String> deviceIds) {
        UserSettings settings = createUserSettings(userId);
        settings.setAllowedDeviceIds(deviceIds);
        return settings;
    }

    public static CurrentlyPlayingTrackInfo createTrackInfo() {
        return createTrackInfo("track-123", "Test Song", "Test Artist");
    }

    public static CurrentlyPlayingTrackInfo createTrackInfo(String trackId, String trackName, String artistName) {
        return CurrentlyPlayingTrackInfo.builder()
                .trackId(trackId)
                .trackName(trackName)
                .artistName(artistName)
                .isPlaying(true)
                .durationMs(180000) // 3 minutes
                .progressMs(60000)  // 1 minute in
                .deviceId("device-123")
                .deviceName("Test Device")
                .contentType("track")
                .build();
    }

    public static CurrentlyPlayingTrackInfo createPausedTrackInfo() {
        CurrentlyPlayingTrackInfo track = createTrackInfo();
        track.setPlaying(false);
        return track;
    }

    public static CurrentlyPlayingTrackInfo createTrackInfoOnDevice(String deviceId) {
        CurrentlyPlayingTrackInfo track = createTrackInfo();
        track.setDeviceId(deviceId);
        track.setDeviceName("Device " + deviceId);
        return track;
    }

    public static CurrentlyPlayingTrackInfo createEpisodeInfo() {
        return createEpisodeInfo("episode-123", "Test Episode", "Test Show");
    }

    public static CurrentlyPlayingTrackInfo createEpisodeInfo(String episodeId, String episodeName, String showName) {
        return CurrentlyPlayingTrackInfo.builder()
                .trackId(episodeId)
                .trackName(episodeName)
                .artistName(showName)
                .isPlaying(true)
                .durationMs(3600000) // 1 hour
                .progressMs(600000)  // 10 minutes in
                .deviceId("device-123")
                .deviceName("Test Device")
                .contentType("episode")
                .build();
    }

    public static UserSettings createUserSettingsWithSyncContentType(String userId, SyncContentType contentType) {
        UserSettings settings = createUserSettings(userId);
        settings.setSyncContentType(contentType);
        settings.setSyncActive(true);
        return settings;
    }

    public static UserSettings createUserSettingsWithRotatingEmojis(String userId, List<String> emojis) {
        UserSettings settings = createUserSettings(userId);
        settings.setRotatingEmojis(emojis);
        return settings;
    }

    public static OAuthState createOAuthState() {
        return createOAuthState(UUID.randomUUID().toString());
    }

    public static OAuthState createOAuthState(String state) {
        return OAuthState.builder()
                .id(UUID.randomUUID().toString())
                .state(state)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    public static OAuthState createExpiredOAuthState() {
        OAuthState oauthState = createOAuthState();
        oauthState.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return oauthState;
    }

    public static String createSpotifyAccessToken() {
        return "BQD" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String createSpotifyRefreshToken() {
        return "AQA" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String createSlackAccessToken() {
        return "xoxp-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String createSlackBotToken() {
        return "xoxb-" + UUID.randomUUID().toString().replace("-", "");
    }
}
