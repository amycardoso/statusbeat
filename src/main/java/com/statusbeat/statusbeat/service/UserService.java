package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import com.statusbeat.statusbeat.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EncryptionUtil encryptionUtil;

    public Optional<User> findBySlackUserId(String slackUserId) {
        return userRepository.findBySlackUserId(slackUserId);
    }

    public Optional<User> findBySpotifyUserId(String spotifyUserId) {
        return userRepository.findBySpotifyUserId(spotifyUserId);
    }

    public List<User> findAllActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    @Transactional
    public User createOrUpdateUser(String slackUserId, String slackTeamId, String slackAccessToken) {
        Optional<User> existingUser = userRepository.findBySlackUserId(slackUserId);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setSlackAccessToken(slackAccessToken);
            user.setUpdatedAt(LocalDateTime.now());
            user.setActive(true);
            log.info("Updated existing user: {}", slackUserId);
            return userRepository.save(user);
        } else {
            User newUser = User.builder()
                    .slackUserId(slackUserId)
                    .slackTeamId(slackTeamId)
                    .slackAccessToken(slackAccessToken)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            User savedUser = userRepository.save(newUser);

            // Create default settings for new user
            createDefaultSettings(savedUser.getId());

            log.info("Created new user: {}", slackUserId);
            return savedUser;
        }
    }

    @Transactional
    public User updateSpotifyTokens(String userId, String spotifyUserId, String accessToken,
                                    String refreshToken, long expiresIn) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setSpotifyUserId(spotifyUserId);
        user.setEncryptedSpotifyAccessToken(encryptionUtil.encrypt(accessToken));
        user.setEncryptedSpotifyRefreshToken(encryptionUtil.encrypt(refreshToken));
        user.setSpotifyTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        user.setUpdatedAt(LocalDateTime.now());

        log.info("Updated Spotify tokens for user: {}", userId);
        return userRepository.save(user);
    }

    public String getDecryptedSpotifyAccessToken(User user) {
        if (user.getEncryptedSpotifyAccessToken() == null) {
            return null;
        }
        return encryptionUtil.decrypt(user.getEncryptedSpotifyAccessToken());
    }

    public String getDecryptedSpotifyRefreshToken(User user) {
        if (user.getEncryptedSpotifyRefreshToken() == null) {
            return null;
        }
        return encryptionUtil.decrypt(user.getEncryptedSpotifyRefreshToken());
    }

    public boolean isSpotifyTokenExpired(User user) {
        if (user.getSpotifyTokenExpiresAt() == null) {
            return true;
        }
        return LocalDateTime.now().plusMinutes(5).isAfter(user.getSpotifyTokenExpiresAt());
    }

    @Transactional
    public void updateCurrentlyPlaying(String userId, String songId, String title, String artist) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setCurrentlyPlayingSongId(songId);
        user.setCurrentlyPlayingSongTitle(title);
        user.setCurrentlyPlayingArtist(artist);
        user.setLastSyncedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.debug("Updated currently playing for user {}: {} - {}", userId, title, artist);
    }

    @Transactional
    public void clearCurrentlyPlaying(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setCurrentlyPlayingSongId(null);
        user.setCurrentlyPlayingSongTitle(null);
        user.setCurrentlyPlayingArtist(null);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.debug("Cleared currently playing for user {}", userId);
    }

    @Transactional
    public void updateLastSetStatus(String userId, String statusText) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLastSetStatusText(statusText);
        user.setManualStatusSet(false); // Clear manual flag when we set status
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.debug("Updated last set status for user {}: {}", userId, statusText);
    }

    @Transactional
    public void setManualStatusFlag(String userId, boolean manualStatusSet) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setManualStatusSet(manualStatusSet);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.debug("Set manual status flag for user {} to: {}", userId, manualStatusSet);
    }

    @Transactional
    public void updateWorkingHours(String userId, Integer startHourUtc, Integer endHourUtc, boolean enabled) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User settings not found"));

        settings.setSyncStartHour(startHourUtc);
        settings.setSyncEndHour(endHourUtc);
        settings.setWorkingHoursEnabled(enabled);
        settings.setUpdatedAt(LocalDateTime.now());

        userSettingsRepository.save(settings);
        log.info("Updated working hours for user {}: {} - {} UTC (enabled: {})",
                userId, startHourUtc, endHourUtc, enabled);
    }

    @Transactional
    public void updateAllowedDevices(String userId, List<String> deviceIds) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User settings not found"));

        settings.setAllowedDeviceIds(deviceIds);
        settings.setUpdatedAt(LocalDateTime.now());

        userSettingsRepository.save(settings);
        log.info("Updated allowed devices for user {}: {}", userId,
                deviceIds == null ? "all devices" : String.join(", ", deviceIds));
    }

    @Transactional
    public void updateDefaultEmoji(String userId, String emoji) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User settings not found"));

        settings.setDefaultEmoji(emoji);
        settings.setUpdatedAt(LocalDateTime.now());

        userSettingsRepository.save(settings);
        log.info("Updated default emoji for user {}: {}", userId, emoji);
    }

    @Transactional
    public void setTokenInvalidated(String userId, boolean invalidated) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTokenInvalidated(invalidated);
        user.setTokenInvalidatedAt(invalidated ? LocalDateTime.now() : null);
        user.setActive(!invalidated); // Deactivate user when token is invalidated
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.info("Set token invalidated flag for user {} to: {}", userId, invalidated);
    }

    public Optional<UserSettings> getUserSettings(String userId) {
        return userSettingsRepository.findByUserId(userId);
    }

    @Transactional
    public UserSettings updateUserSettings(UserSettings settings) {
        settings.setUpdatedAt(LocalDateTime.now());
        return userSettingsRepository.save(settings);
    }

    private void createDefaultSettings(String userId) {
        UserSettings settings = UserSettings.builder()
                .userId(userId)
                .syncEnabled(true)
                .defaultEmoji(":musical_note:")
                .notificationsEnabled(false)
                .showArtist(true)
                .showSongTitle(true)
                .statusTemplate("{emoji} {title} - {artist}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userSettingsRepository.save(settings);
        log.info("Created default settings for user: {}", userId);
    }
}
