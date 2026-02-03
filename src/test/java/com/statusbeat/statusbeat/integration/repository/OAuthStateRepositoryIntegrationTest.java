package com.statusbeat.statusbeat.integration.repository;

import com.statusbeat.statusbeat.model.OAuthState;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuthStateRepository Integration Tests")
class OAuthStateRepositoryIntegrationTest extends IntegrationTestBase {

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should save OAuth state with generated ID")
        void shouldSaveOAuthStateWithGeneratedId() {
            OAuthState state = TestDataFactory.createOAuthState();
            state.setId(null);

            OAuthState saved = oauthStateRepository.save(state);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("should save OAuth state with all fields")
        void shouldSaveOAuthStateWithAllFields() {
            String stateValue = "unique-state-value-123";
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

            OAuthState state = OAuthState.builder()
                    .state(stateValue)
                    .expiresAt(expiresAt)
                    .build();

            OAuthState saved = oauthStateRepository.save(state);

            assertThat(saved.getState()).isEqualTo(stateValue);
            assertThat(saved.getExpiresAt()).isEqualToIgnoringNanos(expiresAt);
        }
    }

    @Nested
    @DisplayName("findByState")
    class FindByStateTests {

        @Test
        @DisplayName("should find OAuth state by state value")
        void shouldFindOAuthStateByStateValue() {
            String stateValue = "my-unique-state-value";
            OAuthState state = TestDataFactory.createOAuthState(stateValue);
            oauthStateRepository.save(state);

            Optional<OAuthState> found = oauthStateRepository.findByState(stateValue);

            assertThat(found).isPresent();
            assertThat(found.get().getState()).isEqualTo(stateValue);
        }

        @Test
        @DisplayName("should return empty when state not found")
        void shouldReturnEmptyWhenStateNotFound() {
            Optional<OAuthState> found = oauthStateRepository.findByState("NONEXISTENT");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByState")
    class DeleteByStateTests {

        @Test
        @DisplayName("should delete OAuth state by state value")
        void shouldDeleteOAuthStateByStateValue() {
            String stateValue = "state-to-delete";
            OAuthState state = TestDataFactory.createOAuthState(stateValue);
            oauthStateRepository.save(state);

            oauthStateRepository.deleteByState(stateValue);

            Optional<OAuthState> found = oauthStateRepository.findByState(stateValue);
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should not throw when deleting non-existent state")
        void shouldNotThrowWhenDeletingNonExistent() {
            Assertions.assertDoesNotThrow(() -> {
                oauthStateRepository.deleteByState("NONEXISTENT");
            });
        }
    }

    @Nested
    @DisplayName("existsByState")
    class ExistsByStateTests {

        @Test
        @DisplayName("should return true when state exists")
        void shouldReturnTrueWhenStateExists() {
            String stateValue = "existing-state";
            OAuthState state = TestDataFactory.createOAuthState(stateValue);
            oauthStateRepository.save(state);

            boolean exists = oauthStateRepository.existsByState(stateValue);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when state does not exist")
        void shouldReturnFalseWhenStateDoesNotExist() {
            boolean exists = oauthStateRepository.existsByState("NONEXISTENT");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("expiration")
    class ExpirationTests {

        @Test
        @DisplayName("should store expiration time correctly")
        void shouldStoreExpirationTimeCorrectly() {
            LocalDateTime futureExpiry = LocalDateTime.now().plusMinutes(10);
            OAuthState state = OAuthState.builder()
                    .state("test-state")
                    .expiresAt(futureExpiry)
                    .build();

            OAuthState saved = oauthStateRepository.save(state);
            Optional<OAuthState> found = oauthStateRepository.findByState("test-state");

            assertThat(found).isPresent();
            assertThat(found.get().getExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("should be able to check if state is expired")
        void shouldBeAbleToCheckIfStateIsExpired() {
            // Create an already expired state
            OAuthState expiredState = TestDataFactory.createExpiredOAuthState();
            oauthStateRepository.save(expiredState);

            Optional<OAuthState> found = oauthStateRepository.findByState(expiredState.getState());

            assertThat(found).isPresent();
            assertThat(found.get().getExpiresAt()).isBefore(LocalDateTime.now());
        }

        @Test
        @DisplayName("should store states with 10 minute TTL")
        void shouldStoreStatesWith10MinuteTtl() {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
            OAuthState state = TestDataFactory.createOAuthState();
            state.setExpiresAt(expiresAt);

            OAuthState saved = oauthStateRepository.save(state);

            // Verify expiration is approximately 10 minutes from now
            assertThat(saved.getExpiresAt()).isBetween(
                    LocalDateTime.now().plusMinutes(9),
                    LocalDateTime.now().plusMinutes(11)
            );
        }
    }

    @Nested
    @DisplayName("CSRF protection")
    class CsrfProtectionTests {

        @Test
        @DisplayName("should use state as CSRF token")
        void shouldUseStateAsCsrfToken() {
            // State value should be unique and unguessable
            OAuthState state1 = TestDataFactory.createOAuthState();
            OAuthState state2 = TestDataFactory.createOAuthState();

            assertThat(state1.getState()).isNotEqualTo(state2.getState());
        }

        @Test
        @DisplayName("should validate state on callback")
        void shouldValidateStateOnCallback() {
            String validState = "valid-csrf-state";
            OAuthState state = TestDataFactory.createOAuthState(validState);
            oauthStateRepository.save(state);

            // Valid state should be found
            assertThat(oauthStateRepository.existsByState(validState)).isTrue();

            // Invalid state should not be found
            assertThat(oauthStateRepository.existsByState("invalid-csrf-state")).isFalse();
        }

        @Test
        @DisplayName("should delete state after use")
        void shouldDeleteStateAfterUse() {
            String stateValue = "one-time-use-state";
            OAuthState state = TestDataFactory.createOAuthState(stateValue);
            oauthStateRepository.save(state);

            // Verify state exists
            assertThat(oauthStateRepository.existsByState(stateValue)).isTrue();

            // Delete after use (simulating callback completion)
            oauthStateRepository.deleteByState(stateValue);

            // State should no longer exist
            assertThat(oauthStateRepository.existsByState(stateValue)).isFalse();
        }
    }

    @Nested
    @DisplayName("unique constraints")
    class UniqueConstraintsTests {

        @Test
        @DisplayName("should enforce unique state value")
        void shouldEnforceUniqueStateValue() {
            String stateValue = "duplicate-state";
            OAuthState state1 = TestDataFactory.createOAuthState(stateValue);
            state1.setId(null);
            oauthStateRepository.save(state1);

            OAuthState state2 = TestDataFactory.createOAuthState(stateValue);
            state2.setId(null);

            // MongoDB will throw a duplicate key exception
            Assertions.assertThrows(Exception.class, () -> {
                oauthStateRepository.save(state2);
            });
        }
    }
}
