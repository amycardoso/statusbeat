package com.statusbeat.statusbeat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slackUserId;

    private String slackTeamId;

    private String slackAccessToken;  // User token for profile updates

    private String slackBotToken;  // Bot token for App Home and events

    private String spotifyUserId;

    private String encryptedSpotifyAccessToken;

    private String encryptedSpotifyRefreshToken;

    private LocalDateTime spotifyTokenExpiresAt;

    private String currentlyPlayingSongId;

    private String currentlyPlayingSongTitle;

    private String currentlyPlayingArtist;

    private LocalDateTime lastSyncedAt;

    private String lastSetStatusText; // The status text we last set, used to detect manual changes

    private boolean manualStatusSet; // Flag indicating user manually changed their status

    // Token invalidation tracking
    private boolean tokenInvalidated; // Flag indicating Slack or Spotify token has been revoked

    private LocalDateTime tokenInvalidatedAt; // When the token invalidation was detected

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private boolean active;
}
