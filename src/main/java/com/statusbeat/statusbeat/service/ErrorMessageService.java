package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.constants.AppConstants;
import org.springframework.stereotype.Service;

@Service
public class ErrorMessageService {

    public String buildNoDeviceMessage() {
        return """
                :headphones: *No Spotify device found*

                Please open Spotify on your phone, computer, or web player, then try again.

                _Tip: Make sure Spotify is actively running on at least one device._
                """;
    }

    public String buildTokenExpiredMessage(String userId) {
        return """
                :key: *Your Spotify connection expired*

                Please reconnect your Spotify account to continue using StatusBeat.

                Use `/statusbeat reconnect` to get a new connection link.
                """;
    }

    public String buildNothingPlayingMessage() {
        return """
                :play_or_pause_button: *Nothing to resume*

                No music is currently paused. Try playing a song in Spotify first!
                """;
    }

    public String buildAlreadyPausedMessage() {
        return ":pause_button: Playback is already paused";
    }

    public String buildNetworkErrorMessage() {
        return """
                :warning: *Couldn't connect to Spotify*

                There might be a temporary issue. Please try again in a moment.

                If the problem persists, check your internet connection.
                """;
    }

    public String buildRateLimitMessage() {
        return """
                :hourglass: *Too many requests*

                Please wait a moment before trying again.
                """;
    }

    public String buildPremiumRequiredMessage() {
        return """
                :sparkles: *Spotify Premium Required*

                Playback controls are only available for Spotify Premium accounts.

                Upgrade at: https://www.spotify.com/premium/
                """;
    }

    public String buildGenericErrorMessage() {
        return """
                :x: *Something went wrong*

                We couldn't complete your request. Please try again.

                If the problem continues, use `/statusbeat status` to check your connection.
                """;
    }

    public String buildNotConnectedMessage() {
        return """
                :link: *Spotify Not Connected*

                You haven't connected your Spotify account yet.

                Get started at: %s
                """.formatted(AppConstants.SLACK_INSTALL_PATH);
    }

    public String buildReconnectInstructions(String reconnectUrl) {
        return """
                :link: *Reconnect Your Spotify Account*

                Click the link below to reconnect:
                %s

                This will refresh your Spotify connection and fix any authorization issues.
                """.formatted(reconnectUrl);
    }
}
