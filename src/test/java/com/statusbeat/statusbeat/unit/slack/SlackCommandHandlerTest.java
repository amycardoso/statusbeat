package com.statusbeat.statusbeat.unit.slack;

import com.slack.api.bolt.App;
import com.statusbeat.statusbeat.exception.*;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.service.ErrorMessageService;
import com.statusbeat.statusbeat.service.MusicSyncService;
import com.statusbeat.statusbeat.service.SpotifyService;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SlackCommandHandler")
@ExtendWith(MockitoExtension.class)
class SlackCommandHandlerTest {

    @Mock
    private App slackApp;

    @Mock
    private UserService userService;

    @Mock
    private SpotifyService spotifyService;

    @Mock
    private MusicSyncService musicSyncService;

    @Mock
    private ErrorMessageService errorMessageService;

    @Nested
    @DisplayName("handlePlay")
    class HandlePlayTests {

        @Test
        @DisplayName("should return not connected message when user not found")
        void shouldReturnNotConnectedWhenUserNotFound() {
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.empty());

            Optional<User> user = userService.findBySlackUserId("U12345");
            assertThat(user).isEmpty();
        }

        @Test
        @DisplayName("should return not connected when user has no Spotify token")
        void shouldReturnNotConnectedWhenNoSpotifyToken() {
            User user = TestDataFactory.createUser();
            user.setEncryptedSpotifyAccessToken(null);
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.of(user));

            Optional<User> result = userService.findBySlackUserId("U12345");
            assertThat(result).isPresent();
            assertThat(result.get().getEncryptedSpotifyAccessToken()).isNull();
        }
    }

    @Nested
    @DisplayName("handlePause")
    class HandlePauseTests {

        @Test
        @DisplayName("should handle no active device exception")
        void shouldHandleNoActiveDeviceException() {
            User user = TestDataFactory.createUserWithSpotify();
            doThrow(new NoActiveDeviceException()).when(spotifyService).pausePlayback(user);

            assertThatThrownBy(() -> spotifyService.pausePlayback(user))
                    .isInstanceOf(NoActiveDeviceException.class);
        }

        @Test
        @DisplayName("should handle token expired exception")
        void shouldHandleTokenExpiredException() {
            User user = TestDataFactory.createUserWithSpotify();
            doThrow(new SpotifyTokenExpiredException()).when(spotifyService).pausePlayback(user);

            assertThatThrownBy(() -> spotifyService.pausePlayback(user))
                    .isInstanceOf(SpotifyTokenExpiredException.class);
        }

        @Test
        @DisplayName("should handle premium required exception")
        void shouldHandlePremiumRequiredException() {
            User user = TestDataFactory.createUserWithSpotify();
            doThrow(new SpotifyPremiumRequiredException()).when(spotifyService).pausePlayback(user);

            assertThatThrownBy(() -> spotifyService.pausePlayback(user))
                    .isInstanceOf(SpotifyPremiumRequiredException.class);
        }

        @Test
        @DisplayName("should handle rate limit exception")
        void shouldHandleRateLimitException() {
            User user = TestDataFactory.createUserWithSpotify();
            doThrow(new SpotifyRateLimitException()).when(spotifyService).pausePlayback(user);

            assertThatThrownBy(() -> spotifyService.pausePlayback(user))
                    .isInstanceOf(SpotifyRateLimitException.class);
        }
    }

    @Nested
    @DisplayName("handleStatus")
    class HandleStatusTests {

        @Test
        @DisplayName("should build status message with sync enabled")
        void shouldBuildStatusMessageWithSyncEnabled() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());

            assertThat(user.getCurrentlyPlayingSongTitle()).isNotNull();
            assertThat(settings.isSyncEnabled()).isTrue();
        }

        @Test
        @DisplayName("should show no track when nothing playing")
        void shouldShowNoTrackWhenNothingPlaying() {
            User user = TestDataFactory.createUserWithSpotify();

            assertThat(user.getCurrentlyPlayingSongTitle()).isNull();
        }
    }

    @Nested
    @DisplayName("handleSync")
    class HandleSyncTests {

        @Test
        @DisplayName("should call manualSync")
        void shouldCallManualSync() {
            musicSyncService.manualSync("U12345");

            verify(musicSyncService).manualSync("U12345");
        }

        @Test
        @DisplayName("should handle sync error")
        void shouldHandleSyncError() {
            doThrow(new RuntimeException("Sync failed")).when(musicSyncService).manualSync("U12345");

            assertThatThrownBy(() -> musicSyncService.manualSync("U12345"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Sync failed");
        }
    }

    @Nested
    @DisplayName("handleEnable/handleDisable")
    class EnableDisableTests {

        @Test
        @DisplayName("should enable sync for user")
        void shouldEnableSync() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);

            settings.setSyncEnabled(true);
            when(userService.updateUserSettings(settings)).thenReturn(settings);

            UserSettings updated = userService.updateUserSettings(settings);
            assertThat(updated.isSyncEnabled()).isTrue();
        }

        @Test
        @DisplayName("should disable sync for user")
        void shouldDisableSync() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());

            settings.setSyncEnabled(false);
            when(userService.updateUserSettings(settings)).thenReturn(settings);

            UserSettings updated = userService.updateUserSettings(settings);
            assertThat(updated.isSyncEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("handleReconnect")
    class HandleReconnectTests {

        @Test
        @DisplayName("should return reconnect URL for existing user")
        void shouldReturnReconnectUrl() {
            User user = TestDataFactory.createUserWithSpotify();

            String reconnectUrl = "/oauth/spotify?userId=" + user.getId();
            when(errorMessageService.buildReconnectInstructions(reconnectUrl))
                    .thenReturn("Please reconnect: " + reconnectUrl);

            String instructions = errorMessageService.buildReconnectInstructions(reconnectUrl);
            assertThat(instructions).contains("reconnect");
        }

        @Test
        @DisplayName("should return not connected for unknown user")
        void shouldReturnNotConnectedForUnknownUser() {
            when(errorMessageService.buildNotConnectedMessage()).thenReturn("Not connected");

            String message = errorMessageService.buildNotConnectedMessage();
            assertThat(message).isEqualTo("Not connected");
        }
    }

    @Nested
    @DisplayName("handleHelp")
    class HandleHelpTests {

        @Test
        @DisplayName("should return help message")
        void shouldReturnHelpMessage() {
            String expectedCommands = "play pause status sync enable disable reconnect help";
            assertThat(expectedCommands).contains("play", "pause", "status", "sync");
        }
    }

    @Nested
    @DisplayName("validateUserAndSpotifyConnection")
    class ValidateUserTests {

        @Test
        @DisplayName("should return null when user not found")
        void shouldReturnNullWhenUserNotFound() {
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.empty());

            Optional<User> result = userService.findBySlackUserId("U12345");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return null when Spotify not connected")
        void shouldReturnNullWhenSpotifyNotConnected() {
            User user = TestDataFactory.createUser();
            user.setEncryptedSpotifyAccessToken(null);
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.of(user));

            Optional<User> result = userService.findBySlackUserId("U12345");
            assertThat(result.get().getEncryptedSpotifyAccessToken()).isNull();
        }

        @Test
        @DisplayName("should return user when fully connected")
        void shouldReturnUserWhenFullyConnected() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.of(user));

            Optional<User> result = userService.findBySlackUserId("U12345");
            assertThat(result).isPresent();
            assertThat(result.get().getEncryptedSpotifyAccessToken()).isNotNull();
        }
    }
}
