package com.statusbeat.statusbeat.slack;

import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.exception.*;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.service.ErrorMessageService;
import com.statusbeat.statusbeat.service.MusicSyncService;
import com.statusbeat.statusbeat.service.SlackService;
import com.statusbeat.statusbeat.service.SpotifyService;
import com.statusbeat.statusbeat.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackCommandHandler {

    private final App slackApp;
    private final UserService userService;
    private final SpotifyService spotifyService;
    private final MusicSyncService musicSyncService;
    private final SlackService slackService;
    private final ErrorMessageService errorMessageService;

    @PostConstruct
    public void registerCommands() {
        // /statusbeat command router
        slackApp.command("/statusbeat", (req, ctx) -> {
            String text = req.getPayload().getText().trim();
            String[] parts = text.split("\\s+");
            String subCommand = parts.length > 0 ? parts[0].toLowerCase() : "";

            log.info("Received /statusbeat command: {} from user: {}",
                    text, req.getPayload().getUserId());

            return switch (subCommand) {
                case "play" -> handlePlay(req, ctx);
                case "pause" -> handlePause(req, ctx);
                case "status" -> handleStatus(req, ctx);
                case "sync" -> handleSync(req, ctx);
                case "enable" -> handleEnable(req, ctx);
                case "disable" -> handleDisable(req, ctx);
                case "reconnect" -> handleReconnect(req, ctx);
                case "purge" -> handlePurge(req, ctx);
                case "help" -> handleHelp(req, ctx);
                default -> handleHelp(req, ctx);
            };
        });

        log.info("Slack slash commands registered successfully");
    }

    private Response handlePlay(SlashCommandRequest req, SlashCommandContext ctx) {
        return executeSpotifyCommand(req, ctx, "play",
                user -> {
                    spotifyService.resumePlayback(user);
                    return ":arrow_forward: Playback resumed!";
                });
    }

    private Response handlePause(SlashCommandRequest req, SlashCommandContext ctx) {
        return executeSpotifyCommand(req, ctx, "pause",
                user -> {
                    spotifyService.pausePlayback(user);
                    return ":pause_button: Playback paused!";
                });
    }

    private Response handleStatus(SlashCommandRequest req, SlashCommandContext ctx) {
        try {
            String slackUserId = req.getPayload().getUserId();
            Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

            if (userOpt.isEmpty()) {
                return ctx.ack(":x: You need to connect your Spotify account first. Visit " + AppConstants.SLACK_INSTALL_PATH + " to get started.");
            }

            User user = userOpt.get();
            Optional<UserSettings> settingsOpt = userService.getUserSettings(user.getId());

            if (settingsOpt.isEmpty()) {
                return ctx.ack(":x: User settings not found.");
            }

            UserSettings settings = settingsOpt.get();
            String statusMessage = buildStatusMessage(user, settings);

            return ctx.ack(statusMessage);

        } catch (Exception e) {
            log.error("Error handling status command", e);
            return ctx.ack(":x: Failed to get status. Error: " + e.getMessage());
        }
    }

    private Response handleSync(SlashCommandRequest req, SlashCommandContext ctx) {
        try {
            String slackUserId = req.getPayload().getUserId();
            musicSyncService.manualSync(slackUserId);
            return ctx.ack(":arrows_counterclockwise: Manual sync triggered!");

        } catch (Exception e) {
            log.error("Error handling sync command", e);
            return ctx.ack(":x: Failed to sync. Error: " + e.getMessage());
        }
    }

    private Response handleEnable(SlashCommandRequest req, SlashCommandContext ctx) {
        return updateSyncSetting(req, ctx, true, ":white_check_mark: Music sync enabled!", "enable");
    }

    private Response handleDisable(SlashCommandRequest req, SlashCommandContext ctx) {
        return updateSyncSetting(req, ctx, false, ":no_entry: Music sync disabled!", "disable");
    }

    private Response handleReconnect(SlashCommandRequest req, SlashCommandContext ctx) {
        try {
            String slackUserId = req.getPayload().getUserId();
            Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

            if (userOpt.isEmpty()) {
                return ctx.ack(errorMessageService.buildNotConnectedMessage());
            }

            User user = userOpt.get();
            String reconnectUrl = AppConstants.OAUTH_SPOTIFY_PATH + "?userId=" + user.getId();

            return ctx.ack(errorMessageService.buildReconnectInstructions(reconnectUrl));

        } catch (Exception e) {
            log.error("Error handling reconnect command", e);
            return ctx.ack(errorMessageService.buildGenericErrorMessage());
        }
    }

    private Response handlePurge(SlashCommandRequest req, SlashCommandContext ctx) {
        try {
            String slackUserId = req.getPayload().getUserId();
            Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

            if (userOpt.isEmpty()) {
                return ctx.ack(":x: No account found to delete.");
            }

            User user = userOpt.get();

            // Clear Slack status before deleting the account
            try {
                slackService.forceClearUserStatus(user);
            } catch (Exception e) {
                log.warn("Could not clear Slack status during purge for user {}: {}", slackUserId, e.getMessage());
            }

            userService.deleteUserCompletely(user.getId());

            return ctx.ack(":wastebasket: Your StatusBeat account has been deleted. " +
                    "All your data has been permanently removed.");

        } catch (Exception e) {
            log.error("Error handling purge command", e);
            return ctx.ack(":x: Failed to delete account. Please try again.");
        }
    }

    private Response handleHelp(SlashCommandRequest req, SlashCommandContext ctx) {
        String helpMessage = """
                :musical_note: *StatusBeat Commands*

                `/statusbeat play` - Resume Spotify playback
                `/statusbeat pause` - Pause Spotify playback
                `/statusbeat status` - Show current sync status
                `/statusbeat sync` - Manually trigger music sync
                `/statusbeat enable` - Enable automatic music sync
                `/statusbeat disable` - Disable automatic music sync
                `/statusbeat reconnect` - Reconnect your Spotify account
                `/statusbeat purge` - Delete your account and all data
                `/statusbeat help` - Show this help message

                :link: To get started, connect your accounts at: %s
                """.formatted(AppConstants.SLACK_INSTALL_PATH);

        return ctx.ack(helpMessage);
    }

    private String buildStatusMessage(User user, UserSettings settings) {
        StringBuilder message = new StringBuilder();
        message.append("*Your StatusBeat Status*\n\n");

        // Sync status
        message.append("*Sync Status:* ");
        message.append(settings.isSyncEnabled() ? ":white_check_mark: Enabled" : ":no_entry: Disabled");
        message.append("\n");

        // Spotify connection
        message.append("*Spotify:* ");
        message.append(user.getEncryptedSpotifyAccessToken() != null ?
                ":white_check_mark: Connected" : ":x: Not connected");
        message.append("\n");

        // Currently playing
        if (user.getCurrentlyPlayingSongTitle() != null) {
            message.append("*Now Playing:* ");
            message.append(user.getCurrentlyPlayingSongTitle());
            message.append(" - ");
            message.append(user.getCurrentlyPlayingArtist());
            message.append("\n");
        } else {
            message.append("*Now Playing:* Nothing\n");
        }

        // Settings
        message.append("\n*Settings:*\n");
        message.append("• Emoji: ").append(settings.getDefaultEmoji()).append("\n");
        message.append("• Show Artist: ").append(settings.isShowArtist() ? "Yes" : "No").append("\n");
        message.append("• Show Title: ").append(settings.isShowSongTitle() ? "Yes" : "No").append("\n");
        message.append("• Notifications: ").append(settings.isNotificationsEnabled() ? "Enabled" : "Disabled");

        return message.toString();
    }

    // Helper methods to reduce duplication

    private Response executeSpotifyCommand(SlashCommandRequest req, SlashCommandContext ctx,
                                          String commandName, SpotifyCommandAction action) {
        try {
            User user = validateUserAndSpotifyConnection(req, ctx);
            if (user == null) {
                return ctx.ack(errorMessageService.buildNotConnectedMessage());
            }

            String successMessage = action.execute(user);
            return ctx.ack(successMessage);

        } catch (NoActiveDeviceException e) {
            return ctx.ack(errorMessageService.buildNoDeviceMessage());
        } catch (SpotifyTokenExpiredException e) {
            return ctx.ack(errorMessageService.buildTokenExpiredMessage(req.getPayload().getUserId()));
        } catch (SpotifyPremiumRequiredException e) {
            return ctx.ack(errorMessageService.buildPremiumRequiredMessage());
        } catch (SpotifyRateLimitException e) {
            return ctx.ack(errorMessageService.buildRateLimitMessage());
        } catch (SpotifyException e) {
            log.error("Spotify error handling {} command", commandName, e);
            return ctx.ack(errorMessageService.buildNetworkErrorMessage());
        } catch (Exception e) {
            log.error("Unexpected error handling {} command", commandName, e);
            return ctx.ack(errorMessageService.buildGenericErrorMessage());
        }
    }

    private User validateUserAndSpotifyConnection(SlashCommandRequest req, SlashCommandContext ctx) {
        String slackUserId = req.getPayload().getUserId();
        Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        if (user.getEncryptedSpotifyAccessToken() == null) {
            return null;
        }

        return user;
    }

    private Response updateSyncSetting(SlashCommandRequest req, SlashCommandContext ctx,
                                       boolean enabled, String successMessage, String operation) {
        try {
            String slackUserId = req.getPayload().getUserId();
            Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

            if (userOpt.isEmpty()) {
                return ctx.ack(":x: User not found.");
            }

            User user = userOpt.get();
            Optional<UserSettings> settingsOpt = userService.getUserSettings(user.getId());

            if (settingsOpt.isEmpty()) {
                return ctx.ack(":x: User settings not found.");
            }

            UserSettings settings = settingsOpt.get();
            settings.setSyncEnabled(enabled);
            userService.updateUserSettings(settings);

            return ctx.ack(successMessage);

        } catch (Exception e) {
            log.error("Error handling {} command", operation, e);
            return ctx.ack(":x: Failed to " + operation + " sync. Error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SpotifyCommandAction {
        String execute(User user);
    }
}
