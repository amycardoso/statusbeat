package com.statusbeat.statusbeat.service;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.view;

/**
 * Service for managing Slack App Home views using Block Kit.
 * Provides UI for users to view status and configure settings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppHomeService {

    private final UserService userService;
    private final TimezoneService timezoneService;
    private final com.slack.api.Slack slack = com.slack.api.Slack.getInstance();

    public void publishHomeView(String slackUserId, String slackAccessToken) {
        try {
            Optional<User> userOpt = userService.findBySlackUserId(slackUserId);

            View homeView;
            if (userOpt.isEmpty()) {
                homeView = buildNotConnectedView();
            } else {
                User user = userOpt.get();
                Optional<UserSettings> settingsOpt = userService.getUserSettings(user.getId());
                homeView = buildConnectedView(user, settingsOpt.orElse(null));
            }

            MethodsClient client = slack.methods(slackAccessToken);
            client.viewsPublish(ViewsPublishRequest.builder()
                    .userId(slackUserId)
                    .view(homeView)
                    .build());

            log.debug("Published App Home view for user {}", slackUserId);

        } catch (IOException | SlackApiException e) {
            log.error("Error publishing App Home view for user {}", slackUserId, e);
        }
    }

    private View buildNotConnectedView() {
        return view(view -> view
                .type("home")
                .blocks(asBlocks(
                        header(header -> header.text(plainText("Welcome to StatusBeat! :musical_note:"))),
                        divider(),
                        section(section -> section.text(markdownText(
                                "*StatusBeat* syncs your Spotify music to your Slack status automatically!\n\n" +
                                        "To get started, you'll need to connect your Slack and Spotify accounts."
                        ))),
                        actions(actions -> actions
                                .blockId("connect_action")
                                .elements(asElements(
                                        button(button -> button
                                                .text(plainText("Connect Accounts"))
                                                .url(AppConstants.SLACK_INSTALL_PATH)
                                                .style("primary")
                                        )
                                ))
                        ),
                        divider(),
                        context(context -> context.elements(asContextElements(
                                markdownText(":information_source: Your accounts will be securely linked and encrypted.")
                        )))
                ))
        );
    }

    private View buildConnectedView(User user, UserSettings settings) {
        if (settings == null) {
            return buildNotConnectedView();
        }

        boolean tokenInvalidated = user.isTokenInvalidated();
        boolean spotifyConnected = user.getEncryptedSpotifyAccessToken() != null;

        List<LayoutBlock> blocks = new ArrayList<>();

        // Header + tagline
        blocks.add(header(header -> header.text(plainText(":musical_note: StatusBeat"))));
        blocks.add(context(ctx -> ctx.elements(asContextElements(
                markdownText("Your music, your status, automatically.")
        ))));
        blocks.add(divider());

        // Connection
        blocks.addAll(buildConnectionSection(user, spotifyConnected, tokenInvalidated));
        blocks.add(divider());

        // Sync Settings
        blocks.addAll(buildSyncSettingsSection(settings));
        blocks.add(divider());

        // Display
        blocks.addAll(buildDisplaySection(settings));
        blocks.add(divider());

        // Schedule & Devices
        blocks.addAll(buildScheduleAndDevicesSection(settings));
        blocks.add(divider());

        // Actions
        blocks.add(buildActionButtons(settings, tokenInvalidated));
        blocks.add(divider());
        blocks.add(context(ctx -> ctx.elements(asContextElements(
                markdownText(":information_source: Use `/statusbeat help` to see available commands")
        ))));

        return view(v -> v.type("home").blocks(blocks));
    }

    private List<LayoutBlock> buildConnectionSection(
            User user, boolean spotifyConnected, boolean tokenInvalidated) {

        List<LayoutBlock> blocks = new ArrayList<>();

        String spotifyStatus;
        if (tokenInvalidated) {
            spotifyStatus = ":warning: Reconnection Required";
        } else if (spotifyConnected) {
            spotifyStatus = ":white_check_mark: Connected";
        } else {
            spotifyStatus = ":x: Not Connected";
        }

        blocks.add(section(s -> s.fields(asSectionFields(
                markdownText("*:link: Spotify*"),
                markdownText(spotifyStatus)
        ))));

        if (user.getCurrentlyPlayingSongTitle() != null) {
            String artist = user.getCurrentlyPlayingArtist();
            String nowPlaying = (artist != null && !artist.isBlank())
                    ? String.format(":headphones: *Now Playing:* %s — %s",
                            user.getCurrentlyPlayingSongTitle(), artist)
                    : String.format(":headphones: *Now Playing:* %s",
                            user.getCurrentlyPlayingSongTitle());
            blocks.add(section(s -> s.text(markdownText(nowPlaying))));
        } else {
            blocks.add(context(ctx -> ctx.elements(asContextElements(
                    markdownText(":headphones: _Nothing playing right now_")
            ))));
        }

        return blocks;
    }

    private List<LayoutBlock> buildSyncSettingsSection(UserSettings settings) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText(":gear: Sync Settings"))));

        blocks.add(section(s -> s.fields(asSectionFields(
                markdownText("*Status*\n" + (settings.isSyncEnabled()
                        ? ":white_check_mark: Enabled" : ":no_entry: Disabled")),
                markdownText("*Sync*\n" + getSyncStateDisplay(settings))
        ))));

        blocks.add(section(s -> s.fields(asSectionFields(
                markdownText("*Content Type*\n" + getContentTypeDisplay(settings)),
                markdownText("*Emoji*\n" + (settings.getDefaultEmoji() != null
                        ? settings.getDefaultEmoji() : ":musical_note:"))
        ))));

        String rotatingEmojis = getRotatingEmojisDisplay(settings);
        if (!"Not configured".equals(rotatingEmojis)) {
            blocks.add(context(ctx -> ctx.elements(asContextElements(
                    markdownText(":arrows_counterclockwise: Rotating: " + rotatingEmojis)
            ))));
        }

        return blocks;
    }

    private List<LayoutBlock> buildDisplaySection(UserSettings settings) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText(":art: Display"))));

        blocks.add(section(s -> s.fields(asSectionFields(
                markdownText("*Show Artist*\n" + (settings.isShowArtist()
                        ? ":white_check_mark: Yes" : ":x: No")),
                markdownText("*Show Title*\n" + (settings.isShowSongTitle()
                        ? ":white_check_mark: Yes" : ":x: No"))
        ))));

        return blocks;
    }

    private List<LayoutBlock> buildScheduleAndDevicesSection(UserSettings settings) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText(":calendar: Schedule & Devices"))));

        String workingHoursText;
        if (settings.isWorkingHoursEnabled() &&
            settings.getSyncStartHour() != null &&
            settings.getSyncEndHour() != null &&
            settings.getTimezoneOffsetSeconds() != null) {

            String startTimeLocal = timezoneService.convertUtcToLocal(
                    settings.getSyncStartHour(),
                    settings.getTimezoneOffsetSeconds());
            String endTimeLocal = timezoneService.convertUtcToLocal(
                    settings.getSyncEndHour(),
                    settings.getTimezoneOffsetSeconds());

            workingHoursText = String.format(
                    ":clock3: *Working Hours:* %s – %s (your local time)",
                    startTimeLocal, endTimeLocal);
        } else {
            workingHoursText = ":clock3: *Working Hours:* Syncing 24/7";
        }

        blocks.add(section(s -> s.text(markdownText(workingHoursText))));

        String deviceText;
        if (settings.getAllowedDeviceIds() == null || settings.getAllowedDeviceIds().isEmpty()) {
            deviceText = ":computer: *Devices:* Tracking all devices";
        } else {
            int deviceCount = settings.getAllowedDeviceIds().size();
            deviceText = String.format(":computer: *Devices:* Tracking %d selected device%s",
                    deviceCount, deviceCount == 1 ? "" : "s");
        }

        blocks.add(section(s -> s.text(markdownText(deviceText))));

        return blocks;
    }

    private LayoutBlock buildActionButtons(UserSettings settings, boolean tokenInvalidated) {
        if (tokenInvalidated) {
            return actions(actions -> actions
                    .blockId("home_actions")
                    .elements(asElements(
                            button(button -> button
                                    .actionId("reconnect_spotify")
                                    .text(plainText("Reconnect Spotify"))
                                    .style("danger")
                            )
                    ))
            );
        } else if (!settings.isSyncEnabled()) {
            return actions(actions -> actions
                    .blockId("home_actions")
                    .elements(asElements(
                            button(button -> button
                                    .actionId("enable_sync")
                                    .text(plainText("Enable Sync"))
                                    .style("primary")
                            ),
                            button(button -> button
                                    .actionId("configure_emoji")
                                    .text(plainText("Configure Emoji"))
                            ),
                            button(button -> button
                                    .actionId("configure_working_hours")
                                    .text(plainText("Working Hours"))
                            ),
                            button(button -> button
                                    .actionId("configure_devices")
                                    .text(plainText("Select Devices"))
                            )
                    ))
            );
        } else {
            return actions(actions -> actions
                    .blockId("home_actions")
                    .elements(asElements(
                            button(button -> button
                                    .actionId(settings.isSyncActive() ? "stop_sync" : "start_sync")
                                    .text(plainText(settings.isSyncActive() ? "Stop Sync" : "Start Sync"))
                                    .style(settings.isSyncActive() ? "danger" : "primary")
                            ),
                            button(button -> button
                                    .actionId("disable_sync")
                                    .text(plainText("Disable"))
                            ),
                            button(button -> button
                                    .actionId("configure_emoji")
                                    .text(plainText("Configure Emoji"))
                            ),
                            button(button -> button
                                    .actionId("configure_working_hours")
                                    .text(plainText("Working Hours"))
                            ),
                            button(button -> button
                                    .actionId("configure_devices")
                                    .text(plainText("Select Devices"))
                            )
                    ))
            );
        }
    }

    private String getSyncStateDisplay(UserSettings settings) {
        if (!settings.isSyncEnabled()) {
            return ":no_entry: Disabled";
        } else if (settings.isSyncActive()) {
            return ":arrow_forward: Syncing";
        } else {
            return ":double_vertical_bar: Paused";
        }
    }

    private String getContentTypeDisplay(UserSettings settings) {
        if (settings.getSyncContentType() == null) {
            return "Music & Podcasts";
        }
        return switch (settings.getSyncContentType()) {
            case MUSIC -> "Music only";
            case PODCASTS -> "Podcasts only";
            case BOTH -> "Music & Podcasts";
        };
    }

    private String getRotatingEmojisDisplay(UserSettings settings) {
        if (settings.getRotatingEmojis() == null || settings.getRotatingEmojis().isEmpty()) {
            return "Not configured";
        }
        return String.join(" ", settings.getRotatingEmojis());
    }
}
