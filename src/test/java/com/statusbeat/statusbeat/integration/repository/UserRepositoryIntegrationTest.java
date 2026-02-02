package com.statusbeat.statusbeat.integration.repository;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepository Integration Tests")
class UserRepositoryIntegrationTest extends IntegrationTestBase {

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should save user with generated ID")
        void shouldSaveUserWithGeneratedId() {
            User user = TestDataFactory.createUser();
            user.setId(null);

            User saved = userRepository.save(user);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("should save user with all fields")
        void shouldSaveUserWithAllFields() {
            User user = TestDataFactory.createUserWithSpotify();

            User saved = userRepository.save(user);

            assertThat(saved.getSlackUserId()).isEqualTo(user.getSlackUserId());
            assertThat(saved.getSlackTeamId()).isEqualTo(user.getSlackTeamId());
            assertThat(saved.getSpotifyUserId()).isEqualTo(user.getSpotifyUserId());
            assertThat(saved.getEncryptedSpotifyAccessToken()).isNotNull();
        }

        @Test
        @DisplayName("should update existing user")
        void shouldUpdateExistingUser() {
            User user = TestDataFactory.createUser();
            User saved = userRepository.save(user);

            saved.setSlackAccessToken("new-token");
            User updated = userRepository.save(saved);

            assertThat(updated.getId()).isEqualTo(saved.getId());
            assertThat(updated.getSlackAccessToken()).isEqualTo("new-token");
        }
    }

    @Nested
    @DisplayName("findBySlackUserId")
    class FindBySlackUserIdTests {

        @Test
        @DisplayName("should find user by Slack user ID")
        void shouldFindUserBySlackUserId() {
            User user = TestDataFactory.createUser("U12345");
            userRepository.save(user);

            Optional<User> found = userRepository.findBySlackUserId("U12345");

            assertThat(found).isPresent();
            assertThat(found.get().getSlackUserId()).isEqualTo("U12345");
        }

        @Test
        @DisplayName("should return empty when Slack user ID not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> found = userRepository.findBySlackUserId("NONEXISTENT");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findBySlackTeamId")
    class FindBySlackTeamIdTests {

        @Test
        @DisplayName("should find user by Slack team ID")
        void shouldFindUserBySlackTeamId() {
            User user = TestDataFactory.createUser();
            user.setSlackTeamId("T12345");
            userRepository.save(user);

            Optional<User> found = userRepository.findBySlackTeamId("T12345");

            assertThat(found).isPresent();
            assertThat(found.get().getSlackTeamId()).isEqualTo("T12345");
        }
    }

    @Nested
    @DisplayName("findBySpotifyUserId")
    class FindBySpotifyUserIdTests {

        @Test
        @DisplayName("should find user by Spotify user ID")
        void shouldFindUserBySpotifyUserId() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setSpotifyUserId("spotify123");
            userRepository.save(user);

            Optional<User> found = userRepository.findBySpotifyUserId("spotify123");

            assertThat(found).isPresent();
            assertThat(found.get().getSpotifyUserId()).isEqualTo("spotify123");
        }

        @Test
        @DisplayName("should return empty when Spotify user ID not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> found = userRepository.findBySpotifyUserId("NONEXISTENT");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByActiveTrue")
    class FindByActiveTrueTests {

        @Test
        @DisplayName("should find all active users")
        void shouldFindAllActiveUsers() {
            User activeUser1 = TestDataFactory.createUser();
            activeUser1.setActive(true);
            userRepository.save(activeUser1);

            User activeUser2 = TestDataFactory.createUser();
            activeUser2.setActive(true);
            userRepository.save(activeUser2);

            User inactiveUser = TestDataFactory.createUser();
            inactiveUser.setActive(false);
            userRepository.save(inactiveUser);

            List<User> activeUsers = userRepository.findByActiveTrue();

            assertThat(activeUsers).hasSize(2);
            assertThat(activeUsers).allMatch(User::isActive);
        }

        @Test
        @DisplayName("should return empty list when no active users")
        void shouldReturnEmptyListWhenNoActiveUsers() {
            User inactiveUser = TestDataFactory.createUser();
            inactiveUser.setActive(false);
            userRepository.save(inactiveUser);

            List<User> activeUsers = userRepository.findByActiveTrue();

            assertThat(activeUsers).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsBySlackUserId")
    class ExistsBySlackUserIdTests {

        @Test
        @DisplayName("should return true when user exists")
        void shouldReturnTrueWhenUserExists() {
            User user = TestDataFactory.createUser("U12345");
            userRepository.save(user);

            boolean exists = userRepository.existsBySlackUserId("U12345");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when user does not exist")
        void shouldReturnFalseWhenUserDoesNotExist() {
            boolean exists = userRepository.existsBySlackUserId("NONEXISTENT");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete user")
        void shouldDeleteUser() {
            User user = TestDataFactory.createUser();
            User saved = userRepository.save(user);

            userRepository.deleteById(saved.getId());

            Optional<User> found = userRepository.findById(saved.getId());
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should delete all users")
        void shouldDeleteAllUsers() {
            userRepository.save(TestDataFactory.createUser());
            userRepository.save(TestDataFactory.createUser());
            userRepository.save(TestDataFactory.createUser());

            userRepository.deleteAll();

            assertThat(userRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("unique constraints")
    class UniqueConstraintsTests {

        @Test
        @DisplayName("should enforce unique Slack user ID")
        void shouldEnforceUniqueSlackUserId() {
            User user1 = TestDataFactory.createUser("U12345");
            user1.setId(null);
            userRepository.save(user1);

            User user2 = TestDataFactory.createUser("U12345");
            user2.setId(null);

            // MongoDB will throw a duplicate key exception
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
                userRepository.save(user2);
            });
        }
    }
}
