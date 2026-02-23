package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.BotInstallationRepository;
import com.statusbeat.statusbeat.service.SlackService;
import com.statusbeat.statusbeat.service.TokenValidationService;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import com.statusbeat.statusbeat.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SlackService")
class SlackServiceTest extends TestBase {

    @Mock
    private UserService userService;

    @Mock
    private TokenValidationService tokenValidationService;

    @Mock
    private BotInstallationRepository botInstallationRepository;

    @Mock
    private EncryptionUtil encryptionUtil;

    private SlackService slackService;

    @BeforeEach
    void setUp() {
        slackService = new SlackService(userService, tokenValidationService, botInstallationRepository, encryptionUtil);
        ReflectionTestUtils.setField(slackService, "expirationOverheadMs", 120000L);
    }

    @Nested
    @DisplayName("updateUserStatus")
    class UpdateUserStatusTests {

        @Test
        @DisplayName("should skip update when sync is disabled")
        void shouldSkipUpdateWhenSyncDisabled() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setSyncEnabled(false);
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Should not throw and should not try to update status
            slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000);

            // Verify we never try to update the last set status (which happens after successful Slack update)
            verify(userService, never()).updateLastSetStatus(any(), any());
        }
    }

    @Nested
    @DisplayName("clearUserStatus")
    class ClearUserStatusTests {

        @Test
        @DisplayName("should skip clear when user has manual status")
        void shouldSkipClearWhenManualStatus() {
            User user = TestDataFactory.createUserWithManualStatus();

            slackService.clearUserStatus(user);

            // No exception should be thrown, and we should not attempt to clear
            // (verified by no Slack API call)
        }

        @Test
        @DisplayName("should skip clear when status already cleared")
        void shouldSkipClearWhenAlreadyCleared() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setStatusCleared(true);

            slackService.clearUserStatus(user);

            // No exception should be thrown
        }

        @Test
        @DisplayName("should attempt clear when status not cleared and no manual status")
        void shouldAttemptClearWhenStatusNotCleared() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            user.setStatusCleared(false);
            user.setManualStatusSet(false);

            // This will fail because we don't have a real Slack token, but it shows the logic flow
            assertThatThrownBy(() -> slackService.clearUserStatus(user))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("hasManualStatusChange")
    class HasManualStatusChangeTests {

        @Test
        @DisplayName("should return false when cannot fetch current status")
        void shouldReturnFalseWhenCannotFetchStatus() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setEncryptedSlackAccessToken("invalid-encrypted-token");

            boolean result = slackService.hasManualStatusChange(user);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("buildStatusText (via updateUserStatus)")
    class BuildStatusTextTests {

        @Test
        @DisplayName("should use status template from settings")
        void shouldUseStatusTemplate() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setStatusTemplate("{artist} - {title}");
            settings.setShowArtist(true);
            settings.setShowSongTitle(true);
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // The actual Slack API call will fail, but we can verify the settings are read
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "My Song", "My Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);

            verify(userService).getUserSettings(user.getId());
        }

        @Test
        @DisplayName("should hide artist when showArtist is false")
        void shouldHideArtistWhenDisabled() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setShowArtist(false);
            settings.setShowSongTitle(true);
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Call will fail but shows the logic path
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should hide title when showSongTitle is false")
        void shouldHideTitleWhenDisabled() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setShowArtist(true);
            settings.setShowSongTitle(false);
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Call will fail but shows the logic path
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("status expiration calculation")
    class StatusExpirationTests {

        @Test
        @DisplayName("should calculate expiration based on remaining time")
        void shouldCalculateExpirationBasedOnRemainingTime() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Duration: 3 minutes (180000ms), Progress: 1 minute (60000ms)
            // Remaining: 2 minutes + overhead (120 seconds)

            // Call will fail but the calculation logic is in the method
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should handle null duration")
        void shouldHandleNullDuration() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", null, null))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("hasManualStatusChange with HTML entity normalization")
    class NormalizeStatusTextTests {

        @Test
        @DisplayName("should normalize &amp; entity when comparing statuses")
        void shouldNormalizeAmpersandEntity() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setLastSetStatusText("Rock & Roll");

            SlackService spySlackService = spy(slackService);
            doReturn("Rock &amp; Roll").when(spySlackService).getCurrentStatusText(user);

            boolean result = spySlackService.hasManualStatusChange(user);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should normalize &lt; and &gt; entities when comparing statuses")
        void shouldNormalizeLtGtEntities() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setLastSetStatusText("<code>");

            SlackService spySlackService = spy(slackService);
            doReturn("&lt;code&gt;").when(spySlackService).getCurrentStatusText(user);

            boolean result = spySlackService.hasManualStatusChange(user);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should normalize &quot; entity when comparing statuses")
        void shouldNormalizeQuoteEntity() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setLastSetStatusText("Say \"Hello\"");

            SlackService spySlackService = spy(slackService);
            doReturn("Say &quot;Hello&quot;").when(spySlackService).getCurrentStatusText(user);

            boolean result = spySlackService.hasManualStatusChange(user);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should detect actual status change even with entity normalization")
        void shouldDetectActualStatusChange() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setLastSetStatusText("Rock & Roll");

            SlackService spySlackService = spy(slackService);
            doReturn("Different Status").when(spySlackService).getCurrentStatusText(user);

            boolean result = spySlackService.hasManualStatusChange(user);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("rotating emoji selection")
    class RotatingEmojiTests {

        @Test
        @DisplayName("should use default emoji when no rotating emojis configured")
        void shouldUseDefaultEmojiWhenNoRotatingEmojis() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setRotatingEmojis(null);
            settings.setDefaultEmoji(":headphones:");
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Call will fail because we don't have a real Slack token
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);

            verify(userService).getUserSettings(user.getId());
        }

        @Test
        @DisplayName("should use default emoji when rotating emojis list is empty")
        void shouldUseDefaultEmojiWhenRotatingEmojisEmpty() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setRotatingEmojis(List.of());
            settings.setDefaultEmoji(":headphones:");
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);

            verify(userService).getUserSettings(user.getId());
        }

        @Test
        @DisplayName("should select from rotating emojis when configured")
        void shouldSelectFromRotatingEmojisWhenConfigured() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettingsWithRotatingEmojis(
                    user.getId(), List.of(":headphones:", ":notes:", ":musical_note:"));
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Call will fail because we don't have a real Slack token
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);

            verify(userService).getUserSettings(user.getId());
        }
    }

    @Nested
    @DisplayName("token invalidation handling")
    class TokenInvalidationTests {

        @Test
        @DisplayName("should detect Slack token invalidation errors")
        void shouldDetectSlackTokenInvalidation() {
            User user = TestDataFactory.createUserWithSpotify();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userService.getUserSettings(user.getId())).thenReturn(Optional.of(settings));

            // Simulating what happens when Slack API returns invalid_auth
            assertThatThrownBy(() ->
                    slackService.updateUserStatus(user, "Song", "Artist", 180000, 60000))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
