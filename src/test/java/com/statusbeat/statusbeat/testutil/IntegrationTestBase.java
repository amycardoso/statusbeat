package com.statusbeat.statusbeat.testutil;

import com.statusbeat.statusbeat.repository.BotInstallationRepository;
import com.statusbeat.statusbeat.repository.OAuthStateRepository;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTestBase {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserSettingsRepository userSettingsRepository;

    @Autowired
    protected OAuthStateRepository oauthStateRepository;

    @Autowired
    protected BotInstallationRepository botInstallationRepository;

    @BeforeEach
    @Transactional
    void cleanDatabase() {
        userSettingsRepository.deleteAll();
        oauthStateRepository.deleteAll();
        botInstallationRepository.deleteAll();
        userRepository.deleteAll();
    }
}
