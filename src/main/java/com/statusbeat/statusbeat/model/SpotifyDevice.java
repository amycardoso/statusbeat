package com.statusbeat.statusbeat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Spotify playback device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotifyDevice {
    private String id;
    private String name;
    private String type; // e.g., "Computer", "Smartphone", "Speaker"
    private boolean isActive;
}
