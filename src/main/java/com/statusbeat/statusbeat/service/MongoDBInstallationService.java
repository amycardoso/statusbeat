package com.statusbeat.statusbeat.service;

import com.slack.api.bolt.model.Bot;
import com.slack.api.bolt.model.Installer;
import com.slack.api.bolt.model.builtin.DefaultBot;
import com.slack.api.bolt.model.builtin.DefaultInstaller;
import com.slack.api.bolt.service.InstallationService;
import com.statusbeat.statusbeat.model.BotInstallation;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.BotInstallationRepository;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import com.statusbeat.statusbeat.util.EncryptionUtil;
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
    private final BotInstallationRepository botInstallationRepository;
    private final SlackService slackService;
    private final EncryptionUtil encryptionUtil;
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
            user = existingUser.get();
            user.setEncryptedSlackAccessToken(encryptionUtil.encrypt(installer.getInstallerUserAccessToken()));
            user.setSlackTeamId(installer.getTeamId());
            user.setUpdatedAt(java.time.LocalDateTime.now());
            log.info("Updating existing user: {}", user.getSlackUserId());
        } else {
            user = User.builder()
                    .slackUserId(installer.getInstallerUserId())
                    .slackTeamId(installer.getTeamId())
                    .encryptedSlackAccessToken(encryptionUtil.encrypt(installer.getInstallerUserAccessToken()))
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

        Optional<BotInstallation> existingBot = botInstallationRepository.findByTeamId(bot.getTeamId());

        BotInstallation botInstallation;
        if (existingBot.isPresent()) {
            botInstallation = existingBot.get();
            botInstallation.setEncryptedBotToken(encryptionUtil.encrypt(bot.getBotAccessToken()));
            botInstallation.setBotUserId(bot.getBotUserId());
            botInstallation.setUpdatedAt(java.time.LocalDateTime.now());
        } else {
            botInstallation = BotInstallation.builder()
                    .teamId(bot.getTeamId())
                    .encryptedBotToken(encryptionUtil.encrypt(bot.getBotAccessToken()))
                    .botUserId(bot.getBotUserId())
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
        }

        botInstallationRepository.save(botInstallation);
        log.info("=== BOT TOKEN SAVED === TeamId: {}", bot.getTeamId());
    }

    @Override
    public void deleteBot(Bot bot) throws Exception {
        if (bot != null && bot.getTeamId() != null) {
            botInstallationRepository.deleteByTeamId(bot.getTeamId());
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

        Optional<BotInstallation> botOpt = botInstallationRepository.findByTeamId(teamId);

        if (botOpt.isEmpty()) {
            log.warn("No bot installation found for teamId: {}", teamId);
            return null;
        }

        BotInstallation botInstallation = botOpt.get();

        DefaultBot bot = new DefaultBot();
        bot.setEnterpriseId(enterpriseId);
        bot.setTeamId(teamId);
        bot.setScope("commands,app_mentions:read,chat:write");
        String decryptedBotToken = botInstallation.getEncryptedBotToken() != null
                ? encryptionUtil.decrypt(botInstallation.getEncryptedBotToken())
                : null;
        bot.setBotAccessToken(decryptedBotToken);
        bot.setBotUserId(botInstallation.getBotUserId());
        bot.setInstalledAt(botInstallation.getCreatedAt() != null ?
            botInstallation.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond() : null);

        log.info("Found bot installation for teamId: {}, bot token present: {}",
                teamId, decryptedBotToken != null);
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

        DefaultInstaller installer = new DefaultInstaller();
        installer.setEnterpriseId(enterpriseId);
        installer.setTeamId(teamId);
        installer.setInstallerUserId(user.getSlackUserId());
        installer.setScope("users.profile:write,users.profile:read");
        String decryptedAccessToken = user.getEncryptedSlackAccessToken() != null
                ? encryptionUtil.decrypt(user.getEncryptedSlackAccessToken())
                : null;
        installer.setInstallerUserAccessToken(decryptedAccessToken);
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
