package com.statusbeat.statusbeat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_settings")
public class UserSettings {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private boolean syncEnabled = true;

    @Builder.Default
    private String defaultEmoji = ":musical_note:";

    @Builder.Default
    private boolean notificationsEnabled = false;

    @Builder.Default
    private boolean showArtist = true;

    @Builder.Default
    private boolean showSongTitle = true;

    @Builder.Default
    private String statusTemplate = "{artist} - {title}";

    @Builder.Default
    private Map<String, String> genreEmojiMap = new HashMap<>();

    // Device tracking configuration
    private java.util.List<String> allowedDeviceIds; // List of Spotify device IDs to track (null = all devices)

    // Working hours configuration
    @Builder.Default
    private boolean workingHoursEnabled = false; // Default: sync 24/7

    private Integer syncStartHour; // HHMM format in UTC (e.g., 0900 for 9:00 AM UTC)

    private Integer syncEndHour; // HHMM format in UTC (e.g., 1700 for 5:00 PM UTC)

    private Integer timezoneOffsetSeconds; // User's timezone offset from UTC in seconds

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
