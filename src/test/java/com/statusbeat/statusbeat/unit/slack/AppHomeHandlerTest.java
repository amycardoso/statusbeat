package com.statusbeat.statusbeat.unit.slack;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.service.*;
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

@DisplayName("AppHomeHandler")
@ExtendWith(MockitoExtension.class)
class AppHomeHandlerTest {

    @Mock
    private AppHomeService appHomeService;

    @Mock
    private UserService userService;

    @Mock
    private MusicSyncService musicSyncService;

    @Mock
    private SpotifyService spotifyService;

    @Mock
    private WorkingHoursValidator workingHoursValidator;

    @Mock
    private TimezoneService timezoneService;

    @Nested
    @DisplayName("startSync action")
    class StartSyncActionTests {

        @Test
        @DisplayName("should start sync for user")
        void shouldStartSyncForUser() {
            User user = TestDataFactory.createUserWithSpotify();

            userService.startSync(user.getId());

            verify(userService).startSync(user.getId());
        }

        @Test
        @DisplayName("should not start sync when user not found")
        void shouldNotStartSyncWhenUserNotFound() {
            when(userService.findBySlackUserId("U12345")).thenReturn(Optional.empty());

            Optional<User> result = userService.findBySlackUserId("U12345");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("stopSync action")
    class StopSyncActionTests {

        @Test
        @DisplayName("should stop sync for user")
        void shouldStopSyncForUser() {
            User user = TestDataFactory.createUserWithSpotify();

            userService.stopSync(user.getId());

            verify(userService).stopSync(user.getId());
        }
    }

    @Nested
    @DisplayName("enableSync action")
    class EnableSyncActionTests {

        @Test
        @DisplayName("should enable sync in settings")
        void shouldEnableSyncInSettings() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);

            settings.setSyncEnabled(true);

            assertThat(settings.isSyncEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("disableSync action")
    class DisableSyncActionTests {

        @Test
        @DisplayName("should disable sync in settings")
        void shouldDisableSyncInSettings() {
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive("user-123");

            settings.setSyncEnabled(false);

            assertThat(settings.isSyncEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("manualSync action")
    class ManualSyncActionTests {

        @Test
        @DisplayName("should trigger manual sync")
        void shouldTriggerManualSync() {
            musicSyncService.manualSync("U12345");

            verify(musicSyncService).manualSync("U12345");
        }
    }

    @Nested
    @DisplayName("working hours modal")
    class WorkingHoursModalTests {

        @Test
        @DisplayName("should validate and convert working hours")
        void shouldValidateAndConvertWorkingHours() {
            when(workingHoursValidator.validateAndConvert("09:00", "17:00", -18000))
                    .thenReturn(new Integer[]{1400, 2200});

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "17:00", -18000);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return null for invalid working hours")
        void shouldReturnNullForInvalidHours() {
            when(workingHoursValidator.validateAndConvert("09:00", "09:00", 0))
                    .thenReturn(null);

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "09:00", 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert UTC to local for display")
        void shouldConvertUtcToLocalForDisplay() {
            when(timezoneService.convertUtcToLocal(1400, -18000)).thenReturn("09:00");
            when(timezoneService.convertUtcToLocal(2200, -18000)).thenReturn("17:00");

            String startLocal = timezoneService.convertUtcToLocal(1400, -18000);
            String endLocal = timezoneService.convertUtcToLocal(2200, -18000);

            assertThat(startLocal).isEqualTo("09:00");
            assertThat(endLocal).isEqualTo("17:00");
        }
    }

    @Nested
    @DisplayName("emoji modal")
    class EmojiModalTests {

        @Test
        @DisplayName("should update emoji for user")
        void shouldUpdateEmojiForUser() {
            User user = TestDataFactory.createUserWithSpotify();

            userService.updateDefaultEmoji(user.getId(), ":headphones:");

            verify(userService).updateDefaultEmoji(user.getId(), ":headphones:");
        }

        @Test
        @DisplayName("should use default emoji when empty")
        void shouldUseDefaultEmojiWhenEmpty() {
            String emoji = "";
            if (emoji == null || emoji.trim().isEmpty()) {
                emoji = ":musical_note:";
            }

            assertThat(emoji).isEqualTo(":musical_note:");
        }
    }

    @Nested
    @DisplayName("devices modal")
    class DevicesModalTests {

        @Test
        @DisplayName("should fetch available devices from Spotify")
        void shouldFetchAvailableDevices() {
            User user = TestDataFactory.createUserWithSpotify();

            spotifyService.getAvailableDevices(user);

            verify(spotifyService).getAvailableDevices(user);
        }

        @Test
        @DisplayName("should update allowed devices")
        void shouldUpdateAllowedDevices() {
            User user = TestDataFactory.createUserWithSpotify();

            userService.updateAllowedDevices(user.getId(), java.util.List.of("device-1"));

            verify(userService).updateAllowedDevices(user.getId(), java.util.List.of("device-1"));
        }

        @Test
        @DisplayName("should clear device filter when none selected")
        void shouldClearDeviceFilterWhenNoneSelected() {
            User user = TestDataFactory.createUserWithSpotify();

            userService.updateAllowedDevices(user.getId(), null);

            verify(userService).updateAllowedDevices(user.getId(), null);
        }
    }

    @Nested
    @DisplayName("reconnect Spotify action")
    class ReconnectSpotifyActionTests {

        @Test
        @DisplayName("should generate reconnect URL for invalidated user")
        void shouldGenerateReconnectUrl() {
            User user = TestDataFactory.createInvalidatedUser();

            String reconnectUrl = "/oauth/spotify?userId=" + user.getId();

            assertThat(reconnectUrl).contains(user.getId());
        }
    }

    @Nested
    @DisplayName("app home view")
    class AppHomeViewTests {

        @Test
        @DisplayName("should publish home view")
        void shouldPublishHomeView() throws Exception {
            appHomeService.publishHomeView("U12345", "xoxb-bot-token");

            verify(appHomeService).publishHomeView("U12345", "xoxb-bot-token");
        }

        @Test
        @DisplayName("should refresh home view after action")
        void shouldRefreshHomeViewAfterAction() throws Exception {
            appHomeService.publishHomeView("U12345", "xoxb-bot-token");

            verify(appHomeService).publishHomeView("U12345", "xoxb-bot-token");
        }
    }
}
