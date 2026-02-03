package com.statusbeat.statusbeat.unit.slack;

import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.handler.builtin.BlockActionHandler;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.service.*;
import com.statusbeat.statusbeat.slack.AppHomeHandler;
import com.statusbeat.statusbeat.testutil.TestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AppHomeHandler")
class AppHomeHandlerTest extends TestBase {

    @Mock
    private App slackApp;

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

    @Mock
    private BlockActionRequest blockActionRequest;

    @Mock
    private ActionContext actionContext;

    @Mock
    private BlockActionPayload payload;

    @Mock
    private BlockActionPayload.User payloadUser;

    @Captor
    private ArgumentCaptor<BlockActionHandler> blockActionHandlerCaptor;

    private AppHomeHandler appHomeHandler;

    @BeforeEach
    void setUp() {
        // Use lenient stubbing for infrastructure setup - these are needed for handler registration
        // but not all tests will trigger all registered handlers
        lenient().when(slackApp.event(any(Class.class), any())).thenReturn(slackApp);
        lenient().when(slackApp.blockAction(anyString(), any())).thenReturn(slackApp);
        lenient().when(slackApp.viewSubmission(anyString(), any())).thenReturn(slackApp);

        appHomeHandler = new AppHomeHandler(
                slackApp,
                appHomeService,
                userService,
                musicSyncService,
                spotifyService,
                workingHoursValidator,
                timezoneService
        );
    }

    private BlockActionHandler captureHandler(String actionId) {
        appHomeHandler.registerHandlers();
        verify(slackApp).blockAction(eq(actionId), blockActionHandlerCaptor.capture());
        return blockActionHandlerCaptor.getValue();
    }

    private void setupPayloadUser(String userId) {
        when(blockActionRequest.getPayload()).thenReturn(payload);
        when(payload.getUser()).thenReturn(payloadUser);
        when(payloadUser.getId()).thenReturn(userId);
        when(actionContext.ack()).thenReturn(Response.ok());
        // Use lenient for getBotToken since not all handlers use it (e.g., when user not found)
        lenient().when(actionContext.getBotToken()).thenReturn("xoxb-test-token");
    }

    @Nested
    @DisplayName("start_sync action")
    class StartSyncActionTests {

        @Test
        @DisplayName("should start sync for existing user")
        void shouldStartSyncForExistingUser() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("start_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(userService).startSync(user.getId());
            verify(appHomeService).publishHomeView(slackUserId, "xoxb-test-token");
        }

        @Test
        @DisplayName("should not start sync when user not found")
        void shouldNotStartSyncWhenUserNotFound() throws Exception {
            String slackUserId = "U_NONEXISTENT";

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("start_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(userService, never()).startSync(any());
            verify(appHomeService, never()).publishHomeView(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("stop_sync action")
    class StopSyncActionTests {

        @Test
        @DisplayName("should stop sync for existing user")
        void shouldStopSyncForExistingUser() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("stop_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(userService).stopSync(user.getId());
            verify(appHomeService).publishHomeView(slackUserId, "xoxb-test-token");
        }

        @Test
        @DisplayName("should not stop sync when user not found")
        void shouldNotStopSyncWhenUserNotFound() throws Exception {
            String slackUserId = "U_NONEXISTENT";

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("stop_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(userService, never()).stopSync(any());
        }
    }

    @Nested
    @DisplayName("enable_sync action")
    class EnableSyncActionTests {

        @Test
        @DisplayName("should enable sync in user settings")
        void shouldEnableSyncInSettings() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("enable_sync");
            handler.apply(blockActionRequest, actionContext);

            assertThat(settings.isSyncEnabled()).isTrue();
            verify(userService).updateUserSettings(settings);
            verify(appHomeService).publishHomeView(slackUserId, "xoxb-test-token");
        }

        @Test
        @DisplayName("should not enable sync when settings not found")
        void shouldNotEnableSyncWhenSettingsNotFound() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("enable_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(userService, never()).updateUserSettings(any());
        }
    }

    @Nested
    @DisplayName("disable_sync action")
    class DisableSyncActionTests {

        @Test
        @DisplayName("should disable sync in user settings")
        void shouldDisableSyncInSettings() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(true);
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("disable_sync");
            handler.apply(blockActionRequest, actionContext);

            assertThat(settings.isSyncEnabled()).isFalse();
            verify(userService).updateUserSettings(settings);
            verify(appHomeService).publishHomeView(slackUserId, "xoxb-test-token");
        }
    }

    @Nested
    @DisplayName("manual_sync action")
    class ManualSyncActionTests {

        @Test
        @DisplayName("should trigger manual sync for user")
        void shouldTriggerManualSync() throws Exception {
            String slackUserId = "U12345";
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("manual_sync");
            handler.apply(blockActionRequest, actionContext);

            verify(musicSyncService).manualSync(slackUserId);
            verify(appHomeService).publishHomeView(slackUserId, "xoxb-test-token");
        }
    }

    @Nested
    @DisplayName("configure_working_hours action")
    class ConfigureWorkingHoursActionTests {

        @Test
        @DisplayName("should not open modal when user not found")
        void shouldNotOpenModalWhenUserNotFound() throws Exception {
            String slackUserId = "U_NONEXISTENT";

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("configure_working_hours");
            handler.apply(blockActionRequest, actionContext);

            verify(actionContext).ack();
            verify(userService, never()).getUserSettings(any());
        }

        @Test
        @DisplayName("should not open modal when settings not found")
        void shouldNotOpenModalWhenSettingsNotFound() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("configure_working_hours");
            handler.apply(blockActionRequest, actionContext);

            verify(actionContext).ack();
        }
    }

    @Nested
    @DisplayName("configure_devices action")
    class ConfigureDevicesActionTests {

        @Test
        @DisplayName("should fetch devices from Spotify when user exists")
        void shouldFetchDevicesFromSpotify() throws Exception {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));
            when(spotifyService.getAvailableDevices(user)).thenReturn(List.of());
            setupPayloadUser(slackUserId);
            // These are needed for the modal opening but may not be called depending on devices list
            lenient().when(payload.getTriggerId()).thenReturn("trigger-123");
            lenient().when(actionContext.client()).thenReturn(mock(MethodsClient.class));

            BlockActionHandler handler = captureHandler("configure_devices");
            handler.apply(blockActionRequest, actionContext);

            verify(spotifyService).getAvailableDevices(user);
        }

        @Test
        @DisplayName("should not fetch devices when user not found")
        void shouldNotFetchDevicesWhenUserNotFound() throws Exception {
            String slackUserId = "U_NONEXISTENT";

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("configure_devices");
            handler.apply(blockActionRequest, actionContext);

            verify(spotifyService, never()).getAvailableDevices(any());
        }
    }

    @Nested
    @DisplayName("reconnect_spotify action")
    class ReconnectSpotifyActionTests {

        @Test
        @DisplayName("should send reconnect message when user exists")
        void shouldSendReconnectMessage() throws Exception {
            User user = TestDataFactory.createInvalidatedUser();
            String slackUserId = user.getSlackUserId();

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.of(user));
            setupPayloadUser(slackUserId);
            MethodsClient mockClient = mock(MethodsClient.class);
            when(actionContext.client()).thenReturn(mockClient);
            when(mockClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                    .thenReturn(mock(ChatPostMessageResponse.class));

            BlockActionHandler handler = captureHandler("reconnect_spotify");
            handler.apply(blockActionRequest, actionContext);

            verify(actionContext).client();
        }

        @Test
        @DisplayName("should not send message when user not found")
        void shouldNotSendMessageWhenUserNotFound() throws Exception {
            String slackUserId = "U_NONEXISTENT";

            when(userService.findBySlackUserId(slackUserId)).thenReturn(Optional.empty());
            setupPayloadUser(slackUserId);

            BlockActionHandler handler = captureHandler("reconnect_spotify");
            handler.apply(blockActionRequest, actionContext);

            verify(actionContext, never()).client();
        }
    }
}
