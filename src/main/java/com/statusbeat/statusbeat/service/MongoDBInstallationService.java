package com.statusbeat.statusbeat.service;

import com.slack.api.bolt.model.Bot;
import com.slack.api.bolt.model.Installer;
import com.slack.api.bolt.model.builtin.DefaultBot;
import com.slack.api.bolt.model.builtin.DefaultInstaller;
import com.slack.api.bolt.service.InstallationService;
import com.slack.api.model.Conversation;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDBInstallationService implements InstallationService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SlackService slackService;
    private boolean historicalDataEnabled = false;

    @Override
    public boolean isHistoricalDataEnabled() {
        return historicalDataEnabled;
    }

    @Override
    public void setHistoricalDataEnabled(boolean enabled) {
        this.historicalDataEnabled = enabled;
    }

    @Override
    public void saveInstallerAndBot(Installer installer) throws Exception {
        log.info("=== SAVE INSTALLER AND BOT CALLED ===");
        log.info("TeamId: {}, UserId: {}, AccessToken present: {}",
                installer.getTeamId(),
                installer.getInstallerUserId(),
                installer.getInstallerUserAccessToken() != null);

        if (installer.getInstallerUserId() == null) {
            log.error("Installer userId is null! Cannot save installation.");
            throw new IllegalArgumentException("Installer userId cannot be null");
        }

        // Check if user already exists
        Optional<User> existingUser = userRepository.findBySlackUserId(installer.getInstallerUserId());

        User user;
        if (existingUser.isPresent()) {
            // Update existing user with new Slack token
            user = existingUser.get();
            user.setSlackAccessToken(installer.getInstallerUserAccessToken());
            user.setSlackTeamId(installer.getTeamId());
            user.setUpdatedAt(java.time.LocalDateTime.now());
            log.info("Updating existing user: {}", user.getSlackUserId());
        } else {
            // Create new user
            user = User.builder()
                    .slackUserId(installer.getInstallerUserId())
                    .slackTeamId(installer.getTeamId())
                    .slackAccessToken(installer.getInstallerUserAccessToken())
                    .active(true)
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            log.info("Creating NEW user with slackUserId: {}", user.getSlackUserId());
        }

        User savedUser = userRepository.save(user);
        log.info("=== USER SAVED TO MONGODB === ID: {}, SlackUserId: {}",
                savedUser.getId(), savedUser.getSlackUserId());

        // Create default UserSettings for new users
        if (existingUser.isEmpty()) {
            // Fetch user's timezone from Slack
            Integer timezoneOffset = slackService.getUserTimezoneOffset(
                    installer.getInstallerUserAccessToken(),
                    installer.getInstallerUserId()
            );

            UserSettings defaultSettings = UserSettings.builder()
                    .userId(savedUser.getId())
                    .syncEnabled(true)
                    .defaultEmoji(":musical_note:")
                    .notificationsEnabled(false)
                    .showArtist(true)
                    .showSongTitle(true)
                    .statusTemplate("{title} - {artist}")
                    .timezoneOffsetSeconds(timezoneOffset)
                    .workingHoursEnabled(false) // Sync 24/7 by default
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

            UserSettings savedSettings = userSettingsRepository.save(defaultSettings);
            log.info("=== DEFAULT USER SETTINGS CREATED === ID: {}, UserId: {}, Timezone: {}",
                    savedSettings.getId(), savedSettings.getUserId(), timezoneOffset);
        }

        // Verify the save worked by immediately querying
        Optional<User> verification = userRepository.findBySlackUserId(installer.getInstallerUserId());
        if (verification.isPresent()) {
            log.info("VERIFICATION SUCCESS: User found in DB immediately after save");
        } else {
            log.error("VERIFICATION FAILED: User NOT found in DB after save!");
        }
    }

    @Override
    public void saveBot(Bot bot) throws Exception {
        log.info("=== SAVE BOT CALLED ===");
        log.info("TeamId: {}, BotAccessToken present: {}",
                bot.getTeamId(), bot.getBotAccessToken() != null);

        if (bot.getTeamId() == null) {
            log.error("Bot teamId is null! Cannot save bot.");
            throw new IllegalArgumentException("Bot teamId cannot be null");
        }

        // Find user by team ID and update with bot token
        Optional<User> userOpt = userRepository.findBySlackTeamId(bot.getTeamId());

        if (userOpt.isEmpty()) {
            log.error("No user found for teamId: {} - cannot save bot token", bot.getTeamId());
            return;
        }

        User user = userOpt.get();
        user.setSlackBotToken(bot.getBotAccessToken());
        user.setUpdatedAt(java.time.LocalDateTime.now());

        userRepository.save(user);
        log.info("=== BOT TOKEN SAVED === UserId: {}, TeamId: {}",
                user.getId(), user.getSlackTeamId());
    }

    @Override
    public void deleteBot(Bot bot) throws Exception {
        if (bot != null && bot.getEnterpriseId() != null) {
            userRepository.findBySlackTeamId(bot.getEnterpriseId())
                    .ifPresent(userRepository::delete);
        }
    }

    @Override
    public void deleteInstaller(Installer installer) throws Exception {
        if (installer != null && installer.getEnterpriseId() != null) {
            userRepository.findBySlackTeamId(installer.getEnterpriseId())
                    .ifPresent(userRepository::delete);
        }
    }

    @Override
    public Bot findBot(String enterpriseId, String teamId) {
        log.debug("Finding bot for enterpriseId: {}, teamId: {}", enterpriseId, teamId);

        Optional<User> userOpt = userRepository.findBySlackTeamId(teamId);

        if (userOpt.isEmpty()) {
            log.warn("No installation found for teamId: {}", teamId);
            return null;
        }

        User user = userOpt.get();

        // Create Bot object with actual bot token
        DefaultBot bot = new DefaultBot();
        bot.setEnterpriseId(enterpriseId);
        bot.setTeamId(teamId);
        bot.setScope("commands,app_mentions:read,chat:write");
        bot.setBotAccessToken(user.getSlackBotToken()); // Use actual bot token for App Home
        bot.setBotUserId(user.getSlackUserId());
        bot.setInstalledAt(user.getCreatedAt() != null ?
            user.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond() : null);

        log.info("Found bot installation for teamId: {}, bot token present: {}",
                teamId, user.getSlackBotToken() != null);
        return bot;
    }

    @Override
    public Installer findInstaller(String enterpriseId, String teamId, String userId) {
        log.debug("Finding installer for enterpriseId: {}, teamId: {}, userId: {}",
            enterpriseId, teamId, userId);

        Optional<User> userOpt = userId != null ?
            userRepository.findBySlackUserId(userId) :
            userRepository.findBySlackTeamId(teamId);

        if (userOpt.isEmpty()) {
            log.warn("No installer found for userId: {} or teamId: {}", userId, teamId);
            return null;
        }

        User user = userOpt.get();

        // Create Installer object
        DefaultInstaller installer = new DefaultInstaller();
        installer.setEnterpriseId(enterpriseId);
        installer.setTeamId(teamId);
        installer.setInstallerUserId(user.getSlackUserId());
        installer.setScope("users.profile:write,users.profile:read");
        installer.setInstallerUserAccessToken(user.getSlackAccessToken());
        installer.setInstalledAt(user.getCreatedAt() != null ?
            user.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond() : null);

        log.info("Found installer for userId: {}", userId);
        return installer;
    }

    /**
     * Helper method to find MongoDB User ID by Slack User ID
     * Used after OAuth completion to link Spotify account
     */
    public String findUserIdBySlackUserId(String slackUserId) {
        Optional<User> userOpt = userRepository.findBySlackUserId(slackUserId);
        return userOpt.map(User::getId).orElse(null);
    }
}
