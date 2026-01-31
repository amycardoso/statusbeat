package com.statusbeat.statusbeat.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentlyPlayingTrackInfo {
    private String trackId;
    private String trackName;
    private String artistName;
    private boolean isPlaying;
    private Integer durationMs; // Track duration in milliseconds
    private Integer progressMs; // Current playback position in milliseconds
    private String deviceId; // Spotify device ID
    private String deviceName; // Spotify device name
}
