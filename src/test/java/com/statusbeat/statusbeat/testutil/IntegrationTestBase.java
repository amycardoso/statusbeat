package com.statusbeat.statusbeat.testutil;

import com.statusbeat.statusbeat.repository.OAuthStateRepository;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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

    @BeforeEach
    void cleanDatabase() {
        userSettingsRepository.deleteAll();
        oauthStateRepository.deleteAll();
        userRepository.deleteAll();
    }
}
