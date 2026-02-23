package com.statusbeat.statusbeat.integration.repository;

import com.statusbeat.statusbeat.model.BotInstallation;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BotInstallationRepository Integration Tests")
class BotInstallationRepositoryIntegrationTest extends IntegrationTestBase {

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should save bot installation with generated ID")
        void shouldSaveBotInstallationWithGeneratedId() {
            BotInstallation bot = TestDataFactory.createBotInstallation("T12345");
            bot.setId(null);

            BotInstallation saved = botInstallationRepository.save(bot);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("should save bot installation with all fields")
        void shouldSaveBotInstallationWithAllFields() {
            BotInstallation bot = TestDataFactory.createBotInstallation("T12345");

            BotInstallation saved = botInstallationRepository.save(bot);

            assertThat(saved.getTeamId()).isEqualTo("T12345");
            assertThat(saved.getEncryptedBotToken()).isNotNull();
            assertThat(saved.getBotUserId()).isNotNull();
        }

        @Test
        @DisplayName("should update existing bot installation")
        void shouldUpdateExistingBotInstallation() {
            BotInstallation bot = TestDataFactory.createBotInstallation("T12345");
            BotInstallation saved = botInstallationRepository.save(bot);

            saved.setEncryptedBotToken("new-encrypted-token");
            BotInstallation updated = botInstallationRepository.save(saved);

            assertThat(updated.getId()).isEqualTo(saved.getId());
            assertThat(updated.getEncryptedBotToken()).isEqualTo("new-encrypted-token");
        }
    }

    @Nested
    @DisplayName("findByTeamId")
    class FindByTeamIdTests {

        @Test
        @DisplayName("should find bot installation by team ID")
        void shouldFindByTeamId() {
            BotInstallation bot = TestDataFactory.createBotInstallation("T12345");
            botInstallationRepository.save(bot);

            Optional<BotInstallation> found = botInstallationRepository.findByTeamId("T12345");

            assertThat(found).isPresent();
            assertThat(found.get().getTeamId()).isEqualTo("T12345");
        }

        @Test
        @DisplayName("should return empty when team ID not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<BotInstallation> found = botInstallationRepository.findByTeamId("NONEXISTENT");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByTeamId")
    class DeleteByTeamIdTests {

        @Test
        @DisplayName("should delete bot installation by team ID")
        void shouldDeleteByTeamId() {
            BotInstallation bot = TestDataFactory.createBotInstallation("T12345");
            botInstallationRepository.save(bot);

            botInstallationRepository.deleteByTeamId("T12345");

            Optional<BotInstallation> found = botInstallationRepository.findByTeamId("T12345");
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("unique constraints")
    class UniqueConstraintsTests {

        @Test
        @DisplayName("should enforce unique team ID")
        void shouldEnforceUniqueTeamId() {
            BotInstallation bot1 = TestDataFactory.createBotInstallation("T12345");
            bot1.setId(null);
            botInstallationRepository.save(bot1);

            BotInstallation bot2 = TestDataFactory.createBotInstallation("T12345");
            bot2.setId(null);

            Assertions.assertThrows(Exception.class, () -> {
                botInstallationRepository.save(bot2);
            });
        }
    }

    @Nested
    @DisplayName("independence from User")
    class IndependenceTests {

        @Test
        @DisplayName("should persist bot installation independently from user")
        void shouldPersistIndependentlyFromUser() {
            // Save a user and a bot installation for the same team
            var user = TestDataFactory.createUser();
            user.setSlackTeamId("T_INDEPENDENT");
            userRepository.save(user);

            BotInstallation bot = TestDataFactory.createBotInstallation("T_INDEPENDENT");
            botInstallationRepository.save(bot);

            // Delete the user
            userRepository.delete(user);

            // Bot installation should still exist
            Optional<BotInstallation> found = botInstallationRepository.findByTeamId("T_INDEPENDENT");
            assertThat(found).isPresent();
            assertThat(found.get().getEncryptedBotToken()).isNotNull();
        }
    }
}
