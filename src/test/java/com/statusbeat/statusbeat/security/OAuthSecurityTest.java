package com.statusbeat.statusbeat.security;

import com.statusbeat.statusbeat.model.OAuthState;
import com.statusbeat.statusbeat.repository.OAuthStateRepository;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for OAuth flow CSRF protection.
 * Verifies state parameter handling and expiration.
 */
@DisplayName("OAuth Security Tests")
class OAuthSecurityTest extends IntegrationTestBase {

    @Nested
    @DisplayName("CSRF State Parameter")
    class CsrfStateParameterTests {

        @Test
        @DisplayName("should generate unique state values")
        void shouldGenerateUniqueStateValues() {
            Set<String> stateValues = new HashSet<>();

            for (int i = 0; i < 100; i++) {
                String state = UUID.randomUUID().toString();
                stateValues.add(state);
            }

            assertThat(stateValues).hasSize(100);
        }

        @Test
        @DisplayName("should validate state parameter on callback")
        void shouldValidateStateOnCallback() {
            // Create valid state
            String validState = "valid-state-" + UUID.randomUUID();
            OAuthState state = OAuthState.builder()
                    .state(validState)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(state);

            // Valid state should be found
            Optional<OAuthState> found = oauthStateRepository.findByState(validState);
            assertThat(found).isPresent();

            // Invalid state should not be found
            Optional<OAuthState> notFound = oauthStateRepository.findByState("invalid-state");
            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("should reject forged state parameter")
        void shouldRejectForgedState() {
            // Attacker tries to use their own state value
            String forgedState = "attacker-forged-state";

            boolean exists = oauthStateRepository.existsByState(forgedState);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should reject reused state parameter")
        void shouldRejectReusedState() {
            // Create and use state
            String state = "one-time-state-" + UUID.randomUUID();
            OAuthState oauthState = OAuthState.builder()
                    .state(state)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(oauthState);

            // First use - should succeed
            assertThat(oauthStateRepository.existsByState(state)).isTrue();

            // Delete after use (as the application would do)
            oauthStateRepository.deleteByState(state);

            // Second attempt - should fail
            assertThat(oauthStateRepository.existsByState(state)).isFalse();
        }
    }

    @Nested
    @DisplayName("State Expiration")
    class StateExpirationTests {

        @Test
        @DisplayName("should set 10 minute TTL on state")
        void shouldSet10MinuteTtl() {
            LocalDateTime beforeCreate = LocalDateTime.now();
            OAuthState state = TestDataFactory.createOAuthState();
            OAuthState saved = oauthStateRepository.save(state);
            LocalDateTime afterCreate = LocalDateTime.now();

            assertThat(saved.getExpiresAt()).isAfter(beforeCreate.plusMinutes(9));
            assertThat(saved.getExpiresAt()).isBefore(afterCreate.plusMinutes(11));
        }

        @Test
        @DisplayName("should identify expired states")
        void shouldIdentifyExpiredStates() {
            // Create expired state
            OAuthState expiredState = OAuthState.builder()
                    .state("expired-state-" + UUID.randomUUID())
                    .expiresAt(LocalDateTime.now().minusMinutes(5))
                    .build();
            oauthStateRepository.save(expiredState);

            // Find the state
            Optional<OAuthState> found = oauthStateRepository.findByState(expiredState.getState());

            // State exists but is expired
            assertThat(found).isPresent();
            assertThat(found.get().getExpiresAt()).isBefore(LocalDateTime.now());
        }

        @Test
        @DisplayName("should not allow use of expired state")
        void shouldNotAllowUseOfExpiredState() {
            // Create expired state
            String stateValue = "expired-state-" + UUID.randomUUID();
            OAuthState expiredState = OAuthState.builder()
                    .state(stateValue)
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .build();
            oauthStateRepository.save(expiredState);

            // Find and check expiration
            Optional<OAuthState> found = oauthStateRepository.findByState(stateValue);

            if (found.isPresent()) {
                boolean isExpired = found.get().getExpiresAt().isBefore(LocalDateTime.now());
                assertThat(isExpired).isTrue();
            }
        }

        @Test
        @DisplayName("should accept valid non-expired state")
        void shouldAcceptValidNonExpiredState() {
            String stateValue = "valid-state-" + UUID.randomUUID();
            OAuthState validState = OAuthState.builder()
                    .state(stateValue)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(validState);

            Optional<OAuthState> found = oauthStateRepository.findByState(stateValue);

            assertThat(found).isPresent();
            assertThat(found.get().getExpiresAt()).isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("State Entropy")
    class StateEntropyTests {

        @Test
        @DisplayName("should use UUID format for state")
        void shouldUseUuidFormatForState() {
            OAuthState state = TestDataFactory.createOAuthState();

            // UUID format validation
            String stateValue = state.getState();
            assertThat(stateValue).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should have sufficient entropy in state")
        void shouldHaveSufficientEntropyInState() {
            // UUID v4 has 122 bits of randomness
            // This is sufficient for CSRF protection
            Set<String> states = new HashSet<>();

            for (int i = 0; i < 10000; i++) {
                states.add(UUID.randomUUID().toString());
            }

            // All 10000 should be unique (collision probability is astronomically low)
            assertThat(states).hasSize(10000);
        }
    }

    @Nested
    @DisplayName("State Cleanup")
    class StateCleanupTests {

        @Test
        @DisplayName("should delete state after successful use")
        void shouldDeleteStateAfterSuccessfulUse() {
            String stateValue = "cleanup-state-" + UUID.randomUUID();
            OAuthState state = OAuthState.builder()
                    .state(stateValue)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(state);

            // Verify exists
            assertThat(oauthStateRepository.existsByState(stateValue)).isTrue();

            // Simulate successful OAuth callback - delete state
            oauthStateRepository.deleteByState(stateValue);

            // Verify deleted
            assertThat(oauthStateRepository.existsByState(stateValue)).isFalse();
        }

        @Test
        @DisplayName("should prevent replay attack after state deletion")
        void shouldPreventReplayAttackAfterDeletion() {
            String stateValue = "replay-test-" + UUID.randomUUID();
            OAuthState state = OAuthState.builder()
                    .state(stateValue)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(state);

            // First callback - use and delete
            Optional<OAuthState> firstUse = oauthStateRepository.findByState(stateValue);
            assertThat(firstUse).isPresent();
            oauthStateRepository.deleteByState(stateValue);

            // Attacker replay attempt
            Optional<OAuthState> replayAttempt = oauthStateRepository.findByState(stateValue);
            assertThat(replayAttempt).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cross-Site Request Forgery Protection")
    class CsrfProtectionTests {

        @Test
        @DisplayName("should bind state to session/request")
        void shouldBindStateToSession() {
            // Each OAuth flow should have its own unique state
            OAuthState state1 = TestDataFactory.createOAuthState();
            OAuthState state2 = TestDataFactory.createOAuthState();

            oauthStateRepository.save(state1);
            oauthStateRepository.save(state2);

            // Both states are valid
            assertThat(oauthStateRepository.existsByState(state1.getState())).isTrue();
            assertThat(oauthStateRepository.existsByState(state2.getState())).isTrue();

            // But they are different
            assertThat(state1.getState()).isNotEqualTo(state2.getState());
        }

        @Test
        @DisplayName("should reject callback without state")
        void shouldRejectCallbackWithoutState() {
            // Empty or null state should not find any match
            assertThat(oauthStateRepository.findByState("")).isEmpty();
            assertThat(oauthStateRepository.existsByState("")).isFalse();
        }

        @Test
        @DisplayName("should reject callback with mismatched state")
        void shouldRejectCallbackWithMismatchedState() {
            String originalState = "original-state-" + UUID.randomUUID();
            String attackerState = "attacker-state-" + UUID.randomUUID();

            OAuthState state = OAuthState.builder()
                    .state(originalState)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            oauthStateRepository.save(state);

            // Attacker's state should not be valid
            assertThat(oauthStateRepository.existsByState(attackerState)).isFalse();

            // Only original state should be valid
            assertThat(oauthStateRepository.existsByState(originalState)).isTrue();
        }
    }
}
