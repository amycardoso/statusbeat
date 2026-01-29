package com.statusbeat.statusbeat.service;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.model.view.View;
import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

        return view(view -> view
                .type("home")
                .blocks(asBlocks(
                        header(header -> header.text(plainText(":musical_note: StatusBeat"))),
                        divider(),
                        buildConnectionStatusSection(spotifyConnected, tokenInvalidated),
                        buildCurrentPlayingSection(user),
                        divider(),
                        section(section -> section
                                .text(markdownText("*:gear: Sync Settings*"))
                        ),
                        section(section -> section
                                .text(markdownText(
                                        String.format("*Status:* %s\n*Emoji:* %s\n*Show Artist:* %s\n*Show Title:* %s",
                                                settings.isSyncEnabled() ? ":white_check_mark: Enabled" : ":no_entry: Disabled",
                                                settings.getDefaultEmoji(),
                                                settings.isShowArtist() ? "Yes" : "No",
                                                settings.isShowSongTitle() ? "Yes" : "No"
                                        )
                                ))
                        ),
                        buildWorkingHoursSection(settings),
                        divider(),
                        buildDeviceTrackingSection(settings),
                        divider(),
                        buildActionButtons(settings, tokenInvalidated),
                        divider(),
                        context(context -> context.elements(asContextElements(
                                markdownText(":information_source: Use `/statusbeat help` to see available commands")
                        )))
                ))
        );
    }

    private com.slack.api.model.block.LayoutBlock buildConnectionStatusSection(
            boolean spotifyConnected, boolean tokenInvalidated) {

        String spotifyStatus;
        if (tokenInvalidated) {
            spotifyStatus = ":warning: *Reconnection Required*";
        } else if (spotifyConnected) {
            spotifyStatus = ":white_check_mark: Connected";
        } else {
            spotifyStatus = ":x: Not Connected";
        }

        return section(section -> section
                .text(markdownText(String.format(
                        "*:link: Connection Status*\n\n*Spotify:* %s",
                        spotifyStatus
                )))
        );
    }

    private com.slack.api.model.block.LayoutBlock buildCurrentPlayingSection(User user) {
        String nowPlayingText;
        if (user.getCurrentlyPlayingSongTitle() != null) {
            nowPlayingText = String.format("*:headphones: Now Playing*\n\n%s - %s",
                    user.getCurrentlyPlayingSongTitle(),
                    user.getCurrentlyPlayingArtist());
        } else {
            nowPlayingText = "*:headphones: Now Playing*\n\nNothing is playing right now";
        }

        return section(section -> section.text(markdownText(nowPlayingText)));
    }

    private com.slack.api.model.block.LayoutBlock buildWorkingHoursSection(UserSettings settings) {
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
                    "*:clock3: Working Hours*\n\n*Enabled:* Yes\n*Hours:* %s - %s (your local time)",
                    startTimeLocal, endTimeLocal
            );
        } else {
            workingHoursText = "*:clock3: Working Hours*\n\n*Enabled:* No (syncing 24/7)";
        }

        return section(section -> section.text(markdownText(workingHoursText)));
    }

    private com.slack.api.model.block.LayoutBlock buildDeviceTrackingSection(UserSettings settings) {
        String deviceText;

        if (settings.getAllowedDeviceIds() == null || settings.getAllowedDeviceIds().isEmpty()) {
            deviceText = "*:computer: Device Tracking*\n\n*Tracking:* All devices";
        } else {
            int deviceCount = settings.getAllowedDeviceIds().size();
            deviceText = String.format("*:computer: Device Tracking*\n\n*Tracking:* %d selected device%s",
                    deviceCount, deviceCount == 1 ? "" : "s");
        }

        return section(section -> section.text(markdownText(deviceText)));
    }

    private com.slack.api.model.block.LayoutBlock buildActionButtons(
            UserSettings settings, boolean tokenInvalidated) {

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
        } else {
            return actions(actions -> actions
                    .blockId("home_actions")
                    .elements(asElements(
                            button(button -> button
                                    .actionId(settings.isSyncEnabled() ? "disable_sync" : "enable_sync")
                                    .text(plainText(settings.isSyncEnabled() ? "Disable Sync" : "Enable Sync"))
                                    .style(settings.isSyncEnabled() ? "danger" : "primary")
                            ),
                            button(button -> button
                                    .actionId("manual_sync")
                                    .text(plainText("Sync Now"))
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
}
