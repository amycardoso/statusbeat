package com.statusbeat.statusbeat.testutil;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides mock beans for integration tests.
 * This prevents the real Slack App from being created during tests.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public AppConfig testAppConfig() {
        return AppConfig.builder()
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .signingSecret("test-signing-secret")
                .scope("chat:write")
                .userScope("users.profile:write")
                .oauthInstallPath("/slack/install")
                .oauthRedirectUriPath("/slack/oauth/callback")
                .build();
    }

    @Bean
    @Primary
    public App testSlackApp(AppConfig appConfig) {
        return new App(appConfig);
    }
}
