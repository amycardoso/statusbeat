package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.model.CurrentlyPlayingTrackInfo;
import com.statusbeat.statusbeat.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicSyncService {

    private final UserService userService;
    private final SpotifyService spotifyService;
    private final SlackService slackService;
    private final TimezoneService timezoneService;

    @Value("${statusbeat.sync.polling-interval:10000}")
    private long pollingIntervalMs;

    @Value("${statusbeat.sync.expiration-overhead-ms:120000}")
    private long expirationOverheadMs;

    @Scheduled(fixedDelayString = "${statusbeat.sync.polling-interval}")
    public void syncMusicStatus() {
        log.debug("Starting music status sync cycle");

        List<User> activeUsers = userService.findAllActiveUsers();
        log.debug("Found {} active users to sync", activeUsers.size());

        for (User user : activeUsers) {
            try {
                syncUserMusicStatus(user);
            } catch (Exception e) {
                log.error("Error syncing music status for user {}", user.getSlackUserId(), e);
                // Continue with next user even if this one fails
            }
        }

        log.debug("Completed music status sync cycle");
    }

    private void syncUserMusicStatus(User user) {
        if (user.getEncryptedSpotifyAccessToken() == null) {
            log.debug("User {} has no Spotify token, skipping sync", user.getSlackUserId());
            return;
        }

        // Check if user has token invalidated
        if (user.isTokenInvalidated()) {
            log.debug("User {} has invalidated token, skipping sync", user.getSlackUserId());
            return;
        }

        if (!isWithinWorkingHours(user)) {
            log.debug("User {} is outside working hours, skipping sync", user.getSlackUserId());
            return;
        }

        CurrentlyPlayingTrackInfo currentTrack = spotifyService.getCurrentlyPlayingTrack(user);

        if (currentTrack == null || !currentTrack.isPlaying()) {
            handleNoTrackPlaying(user);
            return;
        }

        if (!isDeviceAllowed(user, currentTrack.getDeviceId())) {
            log.debug("User {} is playing on non-tracked device '{}', skipping sync",
                    user.getSlackUserId(), currentTrack.getDeviceName());
            handleNoTrackPlaying(user);
            return;
        }

        boolean trackChanged = hasTrackChanged(user, currentTrack);
        boolean needsExpirationRefresh = shouldRefreshExpiration(currentTrack);
        boolean shouldUpdateStatus = trackChanged || needsExpirationRefresh;

        if (!user.isManualStatusSet() && slackService.hasManualStatusChange(user)) {
            log.info("User {} has manually changed their status, pausing automatic updates", user.getSlackUserId());
            userService.setManualStatusFlag(user.getId(), true);
            return;
        }

        if (user.isManualStatusSet()) {
            if (trackChanged) {
                log.info("Track changed for user {} while manual status was set, resuming automatic updates",
                        user.getSlackUserId());
                userService.setManualStatusFlag(user.getId(), false);
            } else {
                log.debug("User {} has manual status set, skipping automatic update", user.getSlackUserId());
                return;
            }
        }

        if (trackChanged) {
            log.info("Track changed for user {}: {} - {}",
                    user.getSlackUserId(),
                    currentTrack.getTrackName(),
                    currentTrack.getArtistName());

            userService.updateCurrentlyPlaying(
                    user.getId(),
                    currentTrack.getTrackId(),
                    currentTrack.getTrackName(),
                    currentTrack.getArtistName()
            );
        }

        if (shouldUpdateStatus) {
            if (needsExpirationRefresh && !trackChanged) {
                log.debug("Same track playing for user {}, but expiration approaching - refreshing status", user.getSlackUserId());
            }

            slackService.updateUserStatus(
                    user,
                    currentTrack.getTrackName(),
                    currentTrack.getArtistName(),
                    currentTrack.getDurationMs(),
                    currentTrack.getProgressMs()
            );
        } else {
            log.debug("Same track playing for user {}, expiration still valid - skipping update", user.getSlackUserId());
        }
    }

    private boolean shouldRefreshExpiration(CurrentlyPlayingTrackInfo currentTrack) {
        if (currentTrack.getDurationMs() == null || currentTrack.getProgressMs() == null) {
            return true;
        }

        long remainingMs = currentTrack.getDurationMs() - currentTrack.getProgressMs();
        long refreshThresholdMs = (pollingIntervalMs * 3) + expirationOverheadMs;

        boolean shouldRefresh = remainingMs <= refreshThresholdMs;

        if (shouldRefresh) {
            log.debug("Expiration refresh needed: remaining={}s, threshold={}s",
                    remainingMs / 1000, refreshThresholdMs / 1000);
        }

        return shouldRefresh;
    }

    private void handleNoTrackPlaying(User user) {
        if (user.getCurrentlyPlayingSongId() != null) {
            log.info("No track playing for user {}, clearing status", user.getSlackUserId());
            slackService.clearUserStatus(user);
            userService.clearCurrentlyPlaying(user.getId());
        }
    }

    private boolean hasTrackChanged(User user, CurrentlyPlayingTrackInfo currentTrack) {
        String previousTrackId = user.getCurrentlyPlayingSongId();

        if (previousTrackId == null) {
            return true;
        }

        return !previousTrackId.equals(currentTrack.getTrackId());
    }

    private boolean isDeviceAllowed(User user, String deviceId) {
        if (deviceId == null) {
            log.debug("No device ID available for user {}, allowing sync", user.getSlackUserId());
            return true;
        }

        var settings = userService.getUserSettings(user.getId());

        if (settings.isEmpty()) {
            log.warn("No settings found for user {}, allowing sync", user.getSlackUserId());
            return true;
        }

        var userSettings = settings.get();

        if (userSettings.getAllowedDeviceIds() == null || userSettings.getAllowedDeviceIds().isEmpty()) {
            return true;
        }

        boolean isAllowed = userSettings.getAllowedDeviceIds().contains(deviceId);

        if (!isAllowed) {
            log.debug("Device {} not in allowed list for user {}", deviceId, user.getSlackUserId());
        }

        return isAllowed;
    }

    private boolean isWithinWorkingHours(User user) {
        var settings = userService.getUserSettings(user.getId());

        if (settings.isEmpty()) {
            log.warn("No settings found for user {}, allowing sync", user.getSlackUserId());
            return true;
        }

        var userSettings = settings.get();

        if (!userSettings.isWorkingHoursEnabled()) {
            return true;
        }

        if (userSettings.getSyncStartHour() == null || userSettings.getSyncEndHour() == null) {
            log.debug("Working hours enabled but not configured for user {}, allowing sync", user.getSlackUserId());
            return true;
        }

        boolean isWithin = timezoneService.isWithinWorkingHours(
                userSettings.getSyncStartHour(),
                userSettings.getSyncEndHour()
        );

        if (!isWithin) {
            log.debug("User {} is outside working hours (UTC {} - {}), skipping sync",
                    user.getSlackUserId(),
                    formatHHMM(userSettings.getSyncStartHour()),
                    formatHHMM(userSettings.getSyncEndHour()));
        }

        return isWithin;
    }

    private String formatHHMM(int hhmm) {
        int hour = hhmm / 100;
        int minute = hhmm % 100;
        return String.format("%02d:%02d", hour, minute);
    }

    public void manualSync(String userId) {
        log.info("Manual sync requested for user {}", userId);

        User user = userService.findBySlackUserId(userId)
                .orElseThrow(() -> new RuntimeException(AppConstants.ERROR_USER_NOT_FOUND));

        syncUserMusicStatus(user);
    }
}
