package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.model.CurrentlyPlayingTrackInfo;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.service.*;
import com.statusbeat.statusbeat.testutil.TestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MusicSyncService")
class MusicSyncServiceTest extends TestBase {

    @Mock
    private UserService userService;

    @Mock
    private SpotifyService spotifyService;

    @Mock
    private SlackService slackService;

    @Mock
    private TimezoneService timezoneService;

    private MusicSyncService musicSyncService;

    @BeforeEach
    void setUp() {
        musicSyncService = new MusicSyncService(userService, spotifyService, slackService, timezoneService);
        ReflectionTestUtils.setField(musicSyncService, "pollingIntervalMs", 10000L);
        ReflectionTestUtils.setField(musicSyncService, "expirationOverheadMs", 120000L);
    }

    @Nested
    @DisplayName("syncMusicStatus")
    class SyncMusicStatusTests {

        @Test
        @DisplayName("should sync all active users")
        void shouldSyncAllActiveUsers() {
            User user1 = TestDataFactory.createUserWithSpotify();
            User user2 = TestDataFactory.createUserWithSpotify();
            UserSettings settings1 = TestDataFactory.createUserSettingsWithSyncActive(user1.getId());
            UserSettings settings2 = TestDataFactory.createUserSettingsWithSyncActive(user2.getId());

            when(userService.findAllActiveUsers()).thenReturn(List.of(user1, user2));
            when(userService.getUserSettings(user1.getId())).thenReturn(Optional.of(settings1));
            when(userService.getUserSettings(user2.getId())).thenReturn(Optional.of(settings2));
            when(slackService.hasManualStatusChange(any())).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(any())).thenReturn(null);

            musicSyncService.syncMusicStatus();

            verify(spotifyService, times(2)).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should continue syncing other users when one fails")
        void shouldContinueOnUserError() {
            User user1 = TestDataFactory.createUserWithSpotify();
            User user2 = TestDataFactory.createUserWithSpotify();
            UserSettings settings1 = TestDataFactory.createUserSettingsWithSyncActive(user1.getId());
            UserSettings settings2 = TestDataFactory.createUserSettingsWithSyncActive(user2.getId());

            when(userService.findAllActiveUsers()).thenReturn(List.of(user1, user2));
            when(userService.getUserSettings(user1.getId())).thenReturn(Optional.of(settings1));
            when(userService.getUserSettings(user2.getId())).thenReturn(Optional.of(settings2));
            when(slackService.hasManualStatusChange(any())).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user1)).thenThrow(new RuntimeException("Error"));
            when(spotifyService.getCurrentlyPlayingTrack(user2)).thenReturn(null);

            musicSyncService.syncMusicStatus();

            // Should still try to sync user2
            verify(spotifyService).getCurrentlyPlayingTrack(user2);
        }

        @Test
        @DisplayName("should handle empty active users list")
        void shouldHandleEmptyUsersList() {
            when(userService.findAllActiveUsers()).thenReturn(List.of());

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }
    }

    @Nested
    @DisplayName("syncUserMusicStatus - skip conditions")
    class SkipConditionsTests {

        @Test
        @DisplayName("should skip user without Spotify token")
        void shouldSkipUserWithoutSpotifyToken() {
            User user = TestDataFactory.createUser();
            user.setEncryptedSpotifyAccessToken(null);
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user with invalidated token")
        void shouldSkipUserWithInvalidatedToken() {
            User user = TestDataFactory.createInvalidatedUser();
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user without settings")
        void shouldSkipUserWithoutSettings() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.empty());

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user with sync disabled")
        void shouldSkipUserWithSyncDisabled() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user with sync not active")
        void shouldSkipUserWithSyncNotActive() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(true);
            settings.setSyncActive(false);
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user with manual status set")
        void shouldSkipUserWithManualStatus() {
            User user = TestDataFactory.createUserWithManualStatus();
            user.setEncryptedSpotifyAccessToken("encrypted-token");
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }

        @Test
        @DisplayName("should skip user outside working hours")
        void shouldSkipUserOutsideWorkingHours() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettingsWithWorkingHours(user.getId());
            settings.setSyncActive(true);
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(timezoneService.isWithinWorkingHours(settings.getSyncStartHour(), settings.getSyncEndHour()))
                    .thenReturn(false);

            musicSyncService.syncMusicStatus();

            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }
    }

    @Nested
    @DisplayName("manual status detection")
    class ManualStatusDetectionTests {

        @Test
        @DisplayName("should stop sync when manual status change detected")
        void shouldStopSyncOnManualStatusChange() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(true);

            musicSyncService.syncMusicStatus();

            verify(userService).setManualStatusFlag(user.getId(), true);
            verify(userService).stopSync(user.getId());
            verify(spotifyService, never()).getCurrentlyPlayingTrack(any());
        }
    }

    @Nested
    @DisplayName("track change detection")
    class TrackChangeDetectionTests {

        @Test
        @DisplayName("should update status when track changes")
        void shouldUpdateStatusOnTrackChange() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            CurrentlyPlayingTrackInfo newTrack = TestDataFactory.createTrackInfo(
                    "new-track-id", "New Song", "New Artist");

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(newTrack);

            musicSyncService.syncMusicStatus();

            verify(userService).updateCurrentlyPlaying(user.getId(), "new-track-id", "New Song", "New Artist");
            verify(slackService).updateUserStatus(eq(user), eq("New Song"), eq("New Artist"), any(), any());
        }

        @Test
        @DisplayName("should skip update when same track is playing and no expiration refresh needed")
        void shouldSkipUpdateWhenSameTrack() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            CurrentlyPlayingTrackInfo sameTrack = TestDataFactory.createTrackInfo(
                    user.getCurrentlyPlayingSongId(), "Test Song", "Test Artist");
            // Set progress well within the song (far from end) to avoid expiration refresh
            sameTrack.setProgressMs(10000);  // 10 seconds in
            sameTrack.setDurationMs(300000); // 5 minute song - lots of time remaining

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(sameTrack);

            musicSyncService.syncMusicStatus();

            // Should not update currently playing (same track)
            verify(userService, never()).updateCurrentlyPlaying(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should detect track change when no previous track")
        void shouldDetectTrackChangeWhenNoPreviousTrack() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            CurrentlyPlayingTrackInfo newTrack = TestDataFactory.createTrackInfo();

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(newTrack);

            musicSyncService.syncMusicStatus();

            verify(userService).updateCurrentlyPlaying(eq(user.getId()), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("no track playing")
    class NoTrackPlayingTests {

        @Test
        @DisplayName("should clear status when no track playing")
        void shouldClearStatusWhenNoTrackPlaying() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(null);

            musicSyncService.syncMusicStatus();

            verify(slackService).clearUserStatus(user);
            verify(userService).clearCurrentlyPlaying(user.getId());
            verify(userService).setStatusCleared(user.getId(), true);
        }

        @Test
        @DisplayName("should clear status when track is paused")
        void shouldClearStatusWhenTrackPaused() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            CurrentlyPlayingTrackInfo pausedTrack = TestDataFactory.createPausedTrackInfo();

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(pausedTrack);

            musicSyncService.syncMusicStatus();

            verify(slackService).clearUserStatus(user);
        }

        @Test
        @DisplayName("should not clear status when no previous track")
        void shouldNotClearStatusWhenNoPreviousTrack() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(null);

            musicSyncService.syncMusicStatus();

            verify(slackService, never()).clearUserStatus(any());
        }
    }

    @Nested
    @DisplayName("device filtering")
    class DeviceFilteringTests {

        @Test
        @DisplayName("should skip sync when playing on non-allowed device")
        void shouldSkipSyncOnNonAllowedDevice() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            UserSettings settings = TestDataFactory.createUserSettingsWithDeviceFilter(
                    user.getId(), List.of("allowed-device-1"));
            settings.setSyncActive(true);
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfoOnDevice("other-device");

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(track);

            musicSyncService.syncMusicStatus();

            verify(slackService, never()).updateUserStatus(any(), any(), any(), any(), any());
            // Should clear status instead
            verify(slackService).clearUserStatus(user);
        }

        @Test
        @DisplayName("should sync when playing on allowed device")
        void shouldSyncOnAllowedDevice() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettingsWithDeviceFilter(
                    user.getId(), List.of("allowed-device"));
            settings.setSyncActive(true);
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfoOnDevice("allowed-device");

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(track);

            musicSyncService.syncMusicStatus();

            verify(slackService).updateUserStatus(eq(user), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should sync all devices when no filter set")
        void shouldSyncAllDevicesWhenNoFilter() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncActive(true);
            settings.setAllowedDeviceIds(null);
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfoOnDevice("any-device");

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(track);

            musicSyncService.syncMusicStatus();

            verify(slackService).updateUserStatus(eq(user), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("manualSync")
    class ManualSyncTests {

        @Test
        @DisplayName("should start sync if not already active")
        void shouldStartSyncIfNotActive() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(true);
            settings.setSyncActive(false);

            when(userService.findBySlackUserId(user.getSlackUserId())).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            musicSyncService.manualSync(user.getSlackUserId());

            verify(userService).startSync(user.getId());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userService.findBySlackUserId("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> musicSyncService.manualSync("unknown"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("working hours")
    class WorkingHoursTests {

        @Test
        @DisplayName("should sync when within working hours")
        void shouldSyncWithinWorkingHours() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettingsWithWorkingHours(user.getId());
            settings.setSyncActive(true);
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfo();

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(timezoneService.isWithinWorkingHours(settings.getSyncStartHour(), settings.getSyncEndHour()))
                    .thenReturn(true);
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(track);

            musicSyncService.syncMusicStatus();

            verify(slackService).updateUserStatus(eq(user), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should sync when working hours not enabled")
        void shouldSyncWhenWorkingHoursNotEnabled() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setCurrentlyPlayingSongId(null);
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            settings.setWorkingHoursEnabled(false);
            CurrentlyPlayingTrackInfo track = TestDataFactory.createTrackInfo();

            when(userService.findAllActiveUsers()).thenReturn(List.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(slackService.hasManualStatusChange(user)).thenReturn(false);
            when(spotifyService.getCurrentlyPlayingTrack(user)).thenReturn(track);

            musicSyncService.syncMusicStatus();

            verify(slackService).updateUserStatus(eq(user), any(), any(), any(), any());
            verify(timezoneService, never()).isWithinWorkingHours(any(), any());
        }
    }
}
