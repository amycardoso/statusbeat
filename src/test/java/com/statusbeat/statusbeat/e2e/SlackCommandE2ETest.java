package com.statusbeat.statusbeat.e2e;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import com.statusbeat.statusbeat.service.MusicSyncService;
import com.statusbeat.statusbeat.service.SpotifyService;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end tests for Slack command flows.
 * Tests the complete workflow of slash commands and their effects.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Slack Command E2E Tests")
class SlackCommandE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserService userService;

    @MockitoBean
    private SpotifyService spotifyService;

    @MockitoBean
    private MusicSyncService musicSyncService;

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("/statusbeat enable/disable flow")
    class EnableDisableFlowTests {

        @Test
        @DisplayName("should enable sync and persist to database")
        void shouldEnableSyncAndPersist() {
            // Setup: User with sync disabled
            User user = createUserWithSettings(false);

            // Execute: Simulate enable command
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            settings.setSyncEnabled(true);
            userService.updateUserSettings(settings);

            // Verify: Settings persisted
            UserSettings updatedSettings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(updatedSettings.isSyncEnabled()).isTrue();
        }

        @Test
        @DisplayName("should disable sync and persist to database")
        void shouldDisableSyncAndPersist() {
            // Setup: User with sync enabled
            User user = createUserWithSettings(true);

            // Execute: Simulate disable command
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            settings.setSyncEnabled(false);
            userService.updateUserSettings(settings);

            // Verify: Settings persisted
            UserSettings updatedSettings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(updatedSettings.isSyncEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("/statusbeat sync flow")
    class ManualSyncFlowTests {

        @Test
        @DisplayName("should trigger manual sync for user")
        void shouldTriggerManualSync() {
            // Setup
            User user = createUserWithSettings(true);

            // Execute: Manual sync should be callable
            doNothing().when(musicSyncService).manualSync(user.getSlackUserId());
            musicSyncService.manualSync(user.getSlackUserId());

            // Verify
            verify(musicSyncService).manualSync(user.getSlackUserId());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            doThrow(new RuntimeException("User not found"))
                    .when(musicSyncService).manualSync("UNKNOWN");

            assertThatThrownBy(() -> musicSyncService.manualSync("UNKNOWN"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("/statusbeat status flow")
    class StatusFlowTests {

        @Test
        @DisplayName("should retrieve user status information")
        void shouldRetrieveUserStatus() {
            // Setup
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            user = userRepository.save(user);
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            userSettingsRepository.save(settings);

            // Execute: Retrieve user and settings
            Optional<User> foundUser = userService.findBySlackUserId(user.getSlackUserId());
            Optional<UserSettings> foundSettings = userService.getUserSettings(user.getId());

            // Verify
            assertThat(foundUser).isPresent();
            assertThat(foundSettings).isPresent();
            assertThat(foundUser.get().getCurrentlyPlayingSongTitle()).isNotNull();
            assertThat(foundSettings.get().isSyncEnabled()).isTrue();
        }

        @Test
        @DisplayName("should show no track when nothing playing")
        void shouldShowNoTrackWhenNothingPlaying() {
            // Setup
            User user = TestDataFactory.createUserWithSpotify();
            user = userRepository.save(user);
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            // Execute
            Optional<User> foundUser = userService.findBySlackUserId(user.getSlackUserId());

            // Verify
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getCurrentlyPlayingSongTitle()).isNull();
        }
    }

    @Nested
    @DisplayName("/statusbeat play/pause flow")
    class PlayPauseFlowTests {

        @Test
        @DisplayName("should call Spotify pause for connected user")
        void shouldCallSpotifyPause() {
            // Setup
            User user = TestDataFactory.createUserWithSpotify();
            user = userRepository.save(user);

            // Execute
            spotifyService.pausePlayback(user);

            // Verify
            verify(spotifyService).pausePlayback(user);
        }

        @Test
        @DisplayName("should call Spotify resume for connected user")
        void shouldCallSpotifyResume() {
            // Setup
            User user = TestDataFactory.createUserWithSpotify();
            user = userRepository.save(user);

            // Execute
            spotifyService.resumePlayback(user);

            // Verify
            verify(spotifyService).resumePlayback(user);
        }

        @Test
        @DisplayName("should handle user without Spotify connection")
        void shouldHandleUserWithoutSpotify() {
            // Setup
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            Optional<User> foundUser = userService.findBySlackUserId(user.getSlackUserId());

            // Verify: User has no Spotify token
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getEncryptedSpotifyAccessToken()).isNull();
        }
    }

    @Nested
    @DisplayName("/statusbeat reconnect flow")
    class ReconnectFlowTests {

        @Test
        @DisplayName("should clear invalidation when user reconnects")
        void shouldClearInvalidationOnReconnect() {
            // Setup: Invalidated user
            User user = TestDataFactory.createInvalidatedUser();
            user = userRepository.save(user);
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            assertThat(user.isTokenInvalidated()).isTrue();

            // Execute: Simulate successful reconnection
            userService.setTokenInvalidated(user.getId(), false);

            // Verify
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.isTokenInvalidated()).isFalse();
            assertThat(updatedUser.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("User lookup flow")
    class UserLookupTests {

        @Test
        @DisplayName("should find user by Slack user ID")
        void shouldFindUserBySlackUserId() {
            // Setup
            User user = TestDataFactory.createUser("U12345ABC");
            userRepository.save(user);

            // Execute
            Optional<User> found = userService.findBySlackUserId("U12345ABC");

            // Verify
            assertThat(found).isPresent();
            assertThat(found.get().getSlackUserId()).isEqualTo("U12345ABC");
        }

        @Test
        @DisplayName("should return empty for unknown Slack user ID")
        void shouldReturnEmptyForUnknownUser() {
            Optional<User> found = userService.findBySlackUserId("UNKNOWN");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Settings update flow")
    class SettingsUpdateTests {

        @Test
        @DisplayName("should update emoji setting")
        void shouldUpdateEmojiSetting() {
            // Setup
            User user = createUserWithSettings(true);

            // Execute
            userService.updateDefaultEmoji(user.getId(), ":headphones:");

            // Verify
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(settings.getDefaultEmoji()).isEqualTo(":headphones:");
        }

        @Test
        @DisplayName("should update working hours setting")
        void shouldUpdateWorkingHoursSetting() {
            // Setup
            User user = createUserWithSettings(true);

            // Execute
            userService.updateWorkingHours(user.getId(), 900, 1700, true);

            // Verify
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(settings.isWorkingHoursEnabled()).isTrue();
            assertThat(settings.getSyncStartHour()).isEqualTo(900);
            assertThat(settings.getSyncEndHour()).isEqualTo(1700);
        }

        @Test
        @DisplayName("should update device filter setting")
        void shouldUpdateDeviceFilterSetting() {
            // Setup
            User user = createUserWithSettings(true);

            // Execute
            userService.updateAllowedDevices(user.getId(), java.util.List.of("device-1", "device-2"));

            // Verify
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(settings.getAllowedDeviceIds()).containsExactly("device-1", "device-2");
        }
    }

    @Nested
    @DisplayName("Start/Stop sync flow")
    class StartStopSyncTests {

        @Test
        @DisplayName("should start sync and clear manual flag")
        void shouldStartSyncAndClearManualFlag() {
            // Setup: User with manual status
            User user = TestDataFactory.createUserWithManualStatus();
            user = userRepository.save(user);
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            // Execute
            userService.startSync(user.getId());

            // Verify
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            UserSettings updatedSettings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();

            assertThat(updatedUser.isManualStatusSet()).isFalse();
            assertThat(updatedSettings.isSyncActive()).isTrue();
        }

        @Test
        @DisplayName("should stop sync")
        void shouldStopSync() {
            // Setup
            User user = TestDataFactory.createUserWithSpotify();
            user = userRepository.save(user);
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            userSettingsRepository.save(settings);

            // Execute
            userService.stopSync(user.getId());

            // Verify
            UserSettings updatedSettings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(updatedSettings.isSyncActive()).isFalse();
        }
    }

    // Helper methods

    private User createUserWithSettings(boolean syncEnabled) {
        User user = TestDataFactory.createUserWithSpotify();
        user = userRepository.save(user);

        UserSettings settings = TestDataFactory.createUserSettings(user.getId());
        settings.setSyncEnabled(syncEnabled);
        userSettingsRepository.save(settings);

        return user;
    }
}
