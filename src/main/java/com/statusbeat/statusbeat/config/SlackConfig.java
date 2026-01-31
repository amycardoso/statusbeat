package com.statusbeat.statusbeat.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.model.builtin.DefaultBot;
import com.slack.api.bolt.model.builtin.DefaultInstaller;
import com.slack.api.bolt.service.builtin.oauth.OAuthErrorHandler;
import com.slack.api.bolt.service.builtin.oauth.OAuthV2SuccessHandler;
import com.slack.api.bolt.request.builtin.OAuthCallbackRequest;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.service.MongoDBInstallationService;
import com.statusbeat.statusbeat.service.MongoDBOAuthStateService;
import com.statusbeat.statusbeat.service.OAuthTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SlackConfig {

    @Value("${slack.client-id}")
    private String clientId;

    @Value("${slack.client-secret}")
    private String clientSecret;

    @Value("${slack.signing-secret}")
    private String signingSecret;

    @Value("${slack.oauth.bot-scope}")
    private String botScope;

    @Value("${slack.oauth.user-scope}")
    private String userScope;

    @Value("${slack.oauth.install-path}")
    private String installPath;

    @Value("${slack.oauth.redirect-path}")
    private String redirectPath;

    @Bean
    public AppConfig appConfig() {
        return AppConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .signingSecret(signingSecret)
                .scope(botScope)
                .userScope(userScope)
                .oauthInstallPath(installPath)
                .oauthRedirectUriPath(redirectPath)
                .build();
    }

    @Bean
    public App slackApp(
            AppConfig appConfig,
            MongoDBInstallationService installationService,
            MongoDBOAuthStateService oauthStateService,
            OAuthTemplateService templateService) {

        App app = new App(appConfig);

        // Configure OAuth settings with custom services
        app.asOAuthApp(true);
        app.service(installationService);
        app.service(oauthStateService);

        // OAuth V2 Success Handler - called after Slack OAuth completes
        app.oauthCallback((OAuthV2SuccessHandler) (req, resp, oauthAccess) -> {
            String slackUserId = oauthAccess.getAuthedUser().getId();
            String teamId = oauthAccess.getTeam().getId();
            String userAccessToken = oauthAccess.getAuthedUser().getAccessToken();
            String botAccessToken = oauthAccess.getAccessToken(); // Bot token for App Home

            log.info("=== OAuth Callback - User: {}, Team: {}, User Token: {}, Bot Token: {} ===",
                    slackUserId, teamId, userAccessToken != null, botAccessToken != null);

            try {
                // Create Installer object from OAuth response
                DefaultInstaller installer = new DefaultInstaller();
                installer.setInstallerUserId(slackUserId);
                installer.setTeamId(teamId);
                installer.setInstallerUserAccessToken(userAccessToken);

                // Create Bot object to save bot token
                DefaultBot bot = new DefaultBot();
                bot.setTeamId(teamId);
                bot.setBotAccessToken(botAccessToken);
                bot.setBotUserId(oauthAccess.getBotUserId());

                // Save both installer (user) and bot tokens
                installationService.saveInstallerAndBot(installer);
                installationService.saveBot(bot);

                // Get the user ID we just created/updated
                String userId = installationService.findUserIdBySlackUserId(slackUserId);

                if (userId == null) {
                    log.error("Failed to retrieve user ID after save for slackUserId: {}", slackUserId);
                    String errorHtml = templateService.renderError(
                            "Installation Error",
                            "Something went wrong during the installation process.",
                            "Unable to create user account."
                    );
                    return com.slack.api.bolt.response.Response.builder()
                            .statusCode(500)
                            .contentType("text/html")
                            .body(errorHtml)
                            .build();
                }

                // Generate Spotify OAuth link with userId
                String spotifyAuthLink = AppConstants.OAUTH_SPOTIFY_PATH + "?userId=" + userId;
                String successHtml = templateService.renderSuccess(spotifyAuthLink);

                return com.slack.api.bolt.response.Response.builder()
                        .statusCode(200)
                        .contentType("text/html")
                        .body(successHtml)
                        .build();

            } catch (Exception e) {
                log.error("Error in OAuth callback", e);
                String errorHtml = templateService.renderError(
                        "Installation Error",
                        "Something went wrong during the installation process.",
                        e.getMessage()
                );
                return com.slack.api.bolt.response.Response.builder()
                        .statusCode(500)
                        .contentType("text/html")
                        .body(errorHtml)
                        .build();
            }
        });

        // OAuth Error Handler - called if OAuth fails
        app.oauthCallbackError((OAuthErrorHandler) (req, resp) -> {
            String error = req.getPayload().getError();
            log.error("Slack OAuth error: {}", error);

            String errorHtml = templateService.renderError(
                    "Installation Failed",
                    "The Slack app installation could not be completed.",
                    "Error: " + (error != null ? error : "Unknown error")
            );

            return com.slack.api.bolt.response.Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body(errorHtml)
                    .build();
        });

        log.info("Slack App bean created with config - clientId: {}, signingSecret present: {}",
                clientId != null && !clientId.isEmpty() ? "present" : "missing",
                signingSecret != null && !signingSecret.isEmpty());
        log.info("Using MongoDB-based InstallationService and OAuthStateService");
        log.info("Bolt OAuth endpoints configured: {}, {}", installPath, redirectPath);

        return app;
    }
}

