package com.statusbeat.statusbeat.e2e;

import com.statusbeat.statusbeat.model.CurrentlyPlayingTrackInfo;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import com.statusbeat.statusbeat.service.*;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * End-to-end tests for the full sync flow.
 * Tests the complete workflow from Spotify track detection to Slack status update.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Full Sync Flow E2E Tests")
class FullSyncFlowE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MusicSyncService musicSyncService;

    @MockitoBean
    private SpotifyService spotifyService;

    @MockitoBean
    private SlackService slackService;

    @MockitoBean
    private TimezoneService timezoneService;

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        userRepository.deleteAll();

        // Default mocks
        when(timezoneService.isWithinWorkingHours(any(), any())).thenReturn(true);
        when(slackService.hasManualStatusChange(any())).thenReturn(false);
    }

    @Nested
    @DisplayName("Complete Sync Workflow")
    class CompleteSyncWorkflowTests {

        @Test
        @DisplayName("should sync track from Spotify to Slack status")
        void shouldSyncTrackFromSpotifyToSlack() {
            // Setup: Create user with active sync
            User user = createActiveUser();
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfo(
                    "track-123", "Bohemian Rhapsody", "Queen");

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(track);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Slack status was updated
            verify(slackService).updateUserStatus(
                    any(), eq("Bohemian Rhapsody"), eq("Queen"), any(), any());

            // Verify: User's currently playing info was updated
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getCurrentlyPlayingSongId()).isEqualTo("track-123");
            assertThat(updatedUser.getCurrentlyPlayingSongTitle()).isEqualTo("Bohemian Rhapsody");
            assertThat(updatedUser.getCurrentlyPlayingArtist()).isEqualTo("Queen");
        }

        @Test
        @DisplayName("should clear status when music stops")
        void shouldClearStatusWhenMusicStops() {
            // Setup: User with currently playing track
            User user = createActiveUserWithCurrentlyPlaying();

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(null);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was cleared
            verify(slackService).clearUserStatus(any());

            // Verify: User's currently playing was cleared
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getCurrentlyPlayingSongId()).isNull();
            assertThat(updatedUser.isStatusCleared()).isTrue();
        }

        @Test
        @DisplayName("should update status when track changes")
        void shouldUpdateStatusWhenTrackChanges() {
            // Setup: User listening to one track
            User user = createActiveUserWithCurrentlyPlaying();
            CurrentlyPlayingTrackInfo newTrack = TestDataFactory.createTrackInfo(
                    "new-track-456", "Stairway to Heaven", "Led Zeppelin");

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(newTrack);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was updated with new track
            verify(slackService).updateUserStatus(
                    any(), eq("Stairway to Heaven"), eq("Led Zeppelin"), any(), any());

            // Verify: User's track info was updated
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getCurrentlyPlayingSongId()).isEqualTo("new-track-456");
        }

        @Test
        @DisplayName("should not update when same track is playing")
        void shouldNotUpdateWhenSameTrackPlaying() {
            // Setup: User with specific track
            User user = createActiveUserWithCurrentlyPlaying();
            CurrentlyPlayingTrackInfo sameTrack = TestDataFactory.createTrackInfo(
                    user.getCurrentlyPlayingSongId(), "Test Song", "Test Artist");
            sameTrack.setProgressMs(30000);
            sameTrack.setDurationMs(180000);

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(sameTrack);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was not updated
            verify(slackService, never()).updateUserStatus(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Manual Status Detection")
    class ManualStatusDetectionTests {

        @Test
        @DisplayName("should stop sync when user manually changes status")
        void shouldStopSyncWhenManualStatusChange() {
            // Setup
            User user = createActiveUser();
            when(slackService.hasManualStatusChange(any())).thenReturn(true);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Sync was stopped
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());

            // Verify: User was marked with manual status
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.isManualStatusSet()).isTrue();

            // Verify: Settings sync was stopped
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(settings.isSyncActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Device Filtering")
    class DeviceFilteringTests {

        @Test
        @DisplayName("should sync when playing on allowed device")
        void shouldSyncWhenOnAllowedDevice() {
            // Setup: User with device filter
            User user = createActiveUserWithDeviceFilter(List.of("device-allowed"));
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfoOnDevice("device-allowed");

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(track);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was updated
            verify(slackService).updateUserStatus(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should not sync when playing on non-allowed device")
        void shouldNotSyncWhenOnNonAllowedDevice() {
            // Setup: User with device filter
            User user = createActiveUserWithDeviceFilter(List.of("device-allowed"));
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfoOnDevice("device-not-allowed");

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(track);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was not updated (but was cleared since previous track was set)
            verify(slackService, never()).updateUserStatus(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Working Hours")
    class WorkingHoursTests {

        @Test
        @DisplayName("should sync when within working hours")
        void shouldSyncWithinWorkingHours() {
            // Setup
            User user = createActiveUserWithWorkingHours();
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfo();

            when(timezoneService.isWithinWorkingHours(any(), any())).thenReturn(true);
            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(track);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Status was updated
            verify(slackService).updateUserStatus(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should not sync outside working hours")
        void shouldNotSyncOutsideWorkingHours() {
            // Setup
            User user = createActiveUserWithWorkingHours();
            when(timezoneService.isWithinWorkingHours(any(), any())).thenReturn(false);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Spotify was not called
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }
    }

    @Nested
    @DisplayName("Inactive User Handling")
    class InactiveUserTests {

        @Test
        @DisplayName("should skip users without Spotify token")
        void shouldSkipUsersWithoutSpotifyToken() {
            // Setup: User without Spotify
            User user = TestDataFactory.createUser();
            user.setActive(true);
            user.setEncryptedSpotifyAccessToken(null);
            user = userRepository.save(user);

            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            userSettingsRepository.save(settings);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Spotify was not called
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip users with invalidated token")
        void shouldSkipUsersWithInvalidatedToken() {
            // Setup: User with invalidated token
            User user = TestDataFactory.createInvalidatedUser();
            user = userRepository.save(user);

            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            userSettingsRepository.save(settings);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Spotify was not called
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip users with sync disabled")
        void shouldSkipUsersWithSyncDisabled() {
            // Setup: User with sync disabled
            User user = TestDataFactory.createUserWithSpotify();
            user.setActive(true);
            user = userRepository.save(user);

            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);
            userSettingsRepository.save(settings);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Spotify was not called
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }
    }

    @Nested
    @DisplayName("Multiple Users")
    class MultipleUsersTests {

        @Test
        @DisplayName("should sync all active users")
        void shouldSyncAllActiveUsers() {
            // Setup: Multiple users
            User user1 = createActiveUser();
            User user2 = createActiveUser();
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfo();

            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(track);

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Both users were processed
            verify(spotifyService, times(2)).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should continue processing when one user fails")
        void shouldContinueWhenOneUserFails() {
            // Setup
            User user1 = createActiveUser();
            User user2 = createActiveUser();

            when(spotifyService.getCurrentlyPlayingTrack(argThat(u ->
                    u != null && u.getId().equals(user1.getId()))))
                    .thenThrow(new RuntimeException("Error"));
            when(spotifyService.getCurrentlyPlayingTrack(argThat(u ->
                    u != null && u.getId().equals(user2.getId()))))
                    .thenReturn(TestDataFactory.createTrackInfo());

            // Execute
            musicSyncService.syncMusicStatus();

            // Verify: Second user was still processed
            verify(spotifyService, times(2)).getCurrentlyPlayingTrack(any());
        }
    }

    // Helper methods

    private User createActiveUser() {
        User user = TestDataFactory.createUserWithSpotify();
        user.setActive(true);
        user.setCurrentlyPlayingSongId(null);
        user = userRepository.save(user);

        UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
        userSettingsRepository.save(settings);

        return user;
    }

    private User createActiveUserWithCurrentlyPlaying() {
        User user = TestDataFactory.createUserWithCurrentlyPlaying();
        user.setActive(true);
        user.setStatusCleared(false);
        user = userRepository.save(user);

        UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
        userSettingsRepository.save(settings);

        return user;
    }

    private User createActiveUserWithDeviceFilter(List<String> deviceIds) {
        User user = TestDataFactory.createUserWithCurrentlyPlaying();
        user.setActive(true);
        user = userRepository.save(user);

        UserSettings settings = TestDataFactory.createUserSettingsWithDeviceFilter(user.getId(), deviceIds);
        settings.setSyncActive(true);
        userSettingsRepository.save(settings);

        return user;
    }

    private User createActiveUserWithWorkingHours() {
        User user = TestDataFactory.createUserWithSpotify();
        user.setActive(true);
        user.setCurrentlyPlayingSongId(null);
        user = userRepository.save(user);

        UserSettings settings = TestDataFactory.createUserSettingsWithWorkingHours(user.getId());
        settings.setSyncActive(true);
        userSettingsRepository.save(settings);

        return user;
    }
}
