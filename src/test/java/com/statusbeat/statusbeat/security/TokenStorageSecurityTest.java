package com.statusbeat.statusbeat.security;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import com.statusbeat.statusbeat.util.EncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for token storage.
 * Verifies tokens are encrypted at rest and properly handled.
 */
@DisplayName("Token Storage Security Tests")
class TokenStorageSecurityTest extends IntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Nested
    @DisplayName("Spotify Token Encryption at Rest")
    class SpotifyTokenEncryptionTests {

        @Test
        @DisplayName("should encrypt Spotify access token before storage")
        void shouldEncryptAccessTokenBeforeStorage() {
            // Create user first
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            String plainAccessToken = "BQDtest-spotify-access-token-plain-text";
            String plainRefreshToken = "AQAtest-spotify-refresh-token-plain-text";

            // Update with tokens
            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    plainAccessToken,
                    plainRefreshToken,
                    3600
            );

            // Read from database directly
            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            // Stored token should not be the plain token
            assertThat(savedUser.getEncryptedSpotifyAccessToken()).isNotEqualTo(plainAccessToken);
            assertThat(savedUser.getEncryptedSpotifyRefreshToken()).isNotEqualTo(plainRefreshToken);

            // Stored token should not contain plain token substring
            assertThat(savedUser.getEncryptedSpotifyAccessToken()).doesNotContain("BQD");
            assertThat(savedUser.getEncryptedSpotifyRefreshToken()).doesNotContain("AQA");
        }

        @Test
        @DisplayName("should be able to decrypt stored tokens")
        void shouldBeAbleToDecryptStoredTokens() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            String plainAccessToken = "BQDoriginal-access-token";
            String plainRefreshToken = "AQAoriginal-refresh-token";

            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    plainAccessToken,
                    plainRefreshToken,
                    3600
            );

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            // Decrypt tokens
            String decryptedAccess = userService.getDecryptedSpotifyAccessToken(savedUser);
            String decryptedRefresh = userService.getDecryptedSpotifyRefreshToken(savedUser);

            assertThat(decryptedAccess).isEqualTo(plainAccessToken);
            assertThat(decryptedRefresh).isEqualTo(plainRefreshToken);
        }

        @Test
        @DisplayName("should return null when no token stored")
        void shouldReturnNullWhenNoTokenStored() {
            User user = TestDataFactory.createUser();
            user.setEncryptedSpotifyAccessToken(null);
            user.setEncryptedSpotifyRefreshToken(null);
            user = userRepository.save(user);

            String decryptedAccess = userService.getDecryptedSpotifyAccessToken(user);
            String decryptedRefresh = userService.getDecryptedSpotifyRefreshToken(user);

            assertThat(decryptedAccess).isNull();
            assertThat(decryptedRefresh).isNull();
        }
    }

    @Nested
    @DisplayName("Token Storage Format")
    class TokenStorageFormatTests {

        @Test
        @DisplayName("should store encrypted tokens as Base64")
        void shouldStoreEncryptedTokensAsBase64() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "access-token",
                    "refresh-token",
                    3600
            );

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            // Verify Base64 format
            String encryptedAccess = savedUser.getEncryptedSpotifyAccessToken();

            // Should be valid Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedAccess);
            assertThat(decoded.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("should include IV in stored ciphertext")
        void shouldIncludeIvInStoredCiphertext() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "test-token",
                    "test-refresh",
                    3600
            );

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            byte[] decoded = Base64.getDecoder().decode(savedUser.getEncryptedSpotifyAccessToken());

            // IV is 16 bytes + at least one block of encrypted data (16 bytes with padding)
            assertThat(decoded.length).isGreaterThanOrEqualTo(32);
        }
    }

    @Nested
    @DisplayName("Token Updates")
    class TokenUpdateTests {

        @Test
        @DisplayName("should update encrypted token on refresh")
        void shouldUpdateEncryptedTokenOnRefresh() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            // Initial tokens
            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "initial-access-token",
                    "initial-refresh-token",
                    3600
            );

            User afterInitial = userRepository.findById(user.getId()).orElseThrow();
            String initialEncrypted = afterInitial.getEncryptedSpotifyAccessToken();

            // Refresh tokens
            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "refreshed-access-token",
                    "refreshed-refresh-token",
                    3600
            );

            User afterRefresh = userRepository.findById(user.getId()).orElseThrow();
            String refreshedEncrypted = afterRefresh.getEncryptedSpotifyAccessToken();

            // Encrypted values should be different
            assertThat(refreshedEncrypted).isNotEqualTo(initialEncrypted);

            // Decrypted value should be new token
            String decrypted = userService.getDecryptedSpotifyAccessToken(afterRefresh);
            assertThat(decrypted).isEqualTo("refreshed-access-token");
        }
    }

    @Nested
    @DisplayName("Slack Token Handling")
    class SlackTokenHandlingTests {

        @Test
        @DisplayName("should encrypt Slack access token before storage")
        void shouldEncryptSlackAccessToken() {
            String plainToken = "xoxp-test-slack-token";

            User user = userService.createOrUpdateUser("U_SLACK_TEST", "T_TEAM", plainToken);

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(savedUser.getEncryptedSlackAccessToken()).isNotNull();
            assertThat(savedUser.getEncryptedSlackAccessToken()).isNotEqualTo(plainToken);

            String decrypted = userService.getDecryptedSlackAccessToken(savedUser);
            assertThat(decrypted).isEqualTo(plainToken);
        }

        @Test
        @DisplayName("should encrypt Slack bot token before storage")
        void shouldEncryptSlackBotToken() {
            String plainToken = "xoxb-test-bot-token";

            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            user.setEncryptedSlackBotToken(encryptionUtil.encrypt(plainToken));
            user = userRepository.save(user);

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(savedUser.getEncryptedSlackBotToken()).isNotNull();
            assertThat(savedUser.getEncryptedSlackBotToken()).isNotEqualTo(plainToken);

            String decrypted = userService.getDecryptedSlackBotToken(savedUser);
            assertThat(decrypted).isEqualTo(plainToken);
        }
    }

    @Nested
    @DisplayName("Token Invalidation Handling")
    class TokenInvalidationTests {

        @Test
        @DisplayName("should mark user as invalidated")
        void shouldMarkUserAsInvalidated() {
            User user = TestDataFactory.createUserWithSpotify();
            user = userRepository.save(user);

            userService.setTokenInvalidated(user.getId(), true);

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(savedUser.isTokenInvalidated()).isTrue();
            assertThat(savedUser.getTokenInvalidatedAt()).isNotNull();
            assertThat(savedUser.isActive()).isFalse();
        }

        @Test
        @DisplayName("should retain encrypted tokens after invalidation")
        void shouldRetainEncryptedTokensAfterInvalidation() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            // Set tokens
            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "access-token",
                    "refresh-token",
                    3600
            );

            String originalEncrypted = userRepository.findById(user.getId())
                    .orElseThrow().getEncryptedSpotifyAccessToken();

            // Invalidate
            userService.setTokenInvalidated(user.getId(), true);

            // Tokens should still be there (encrypted)
            User savedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(savedUser.getEncryptedSpotifyAccessToken()).isEqualTo(originalEncrypted);
        }

        @Test
        @DisplayName("should clear invalidation on reauthorization")
        void shouldClearInvalidationOnReauthorization() {
            User user = TestDataFactory.createUser();
            user.setTokenInvalidated(true);
            user.setActive(false);
            user = userRepository.save(user);

            // Simulate reauthorization
            userService.setTokenInvalidated(user.getId(), false);

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(savedUser.isTokenInvalidated()).isFalse();
            assertThat(savedUser.getTokenInvalidatedAt()).isNull();
            assertThat(savedUser.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Expiration Handling")
    class TokenExpirationTests {

        @Test
        @DisplayName("should store token expiration time")
        void shouldStoreTokenExpirationTime() {
            User user = TestDataFactory.createUser();
            user = userRepository.save(user);

            // Token expires in 1 hour
            userService.updateSpotifyTokens(
                    user.getId(),
                    "spotify-user-id",
                    "access-token",
                    "refresh-token",
                    3600
            );

            User savedUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(savedUser.getSpotifyTokenExpiresAt()).isNotNull();
            assertThat(savedUser.getSpotifyTokenExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("should detect expired token")
        void shouldDetectExpiredToken() {
            User user = TestDataFactory.createUserWithExpiredSpotifyToken();
            user = userRepository.save(user);

            boolean isExpired = userService.isSpotifyTokenExpired(user);

            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should detect token expiring soon")
        void shouldDetectTokenExpiringSoon() {
            User user = TestDataFactory.createUserWithSpotify();
            // Token expires in 3 minutes (less than 5 minute buffer)
            user.setSpotifyTokenExpiresAt(java.time.LocalDateTime.now().plusMinutes(3));
            user = userRepository.save(user);

            boolean isExpired = userService.isSpotifyTokenExpired(user);

            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should allow valid non-expiring token")
        void shouldAllowValidNonExpiringToken() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setSpotifyTokenExpiresAt(java.time.LocalDateTime.now().plusHours(1));
            user = userRepository.save(user);

            boolean isExpired = userService.isSpotifyTokenExpired(user);

            assertThat(isExpired).isFalse();
        }
    }

    @Nested
    @DisplayName("Data Isolation")
    class DataIsolationTests {

        @Test
        @DisplayName("should isolate tokens between users")
        void shouldIsolateTokensBetweenUsers() {
            User user1 = TestDataFactory.createUser("U1");
            User user2 = TestDataFactory.createUser("U2");
            user1 = userRepository.save(user1);
            user2 = userRepository.save(user2);

            // Set different tokens for each user
            userService.updateSpotifyTokens(user1.getId(), "spotify1", "token1", "refresh1", 3600);
            userService.updateSpotifyTokens(user2.getId(), "spotify2", "token2", "refresh2", 3600);

            // Verify isolation
            User savedUser1 = userRepository.findById(user1.getId()).orElseThrow();
            User savedUser2 = userRepository.findById(user2.getId()).orElseThrow();

            String decrypted1 = userService.getDecryptedSpotifyAccessToken(savedUser1);
            String decrypted2 = userService.getDecryptedSpotifyAccessToken(savedUser2);

            assertThat(decrypted1).isEqualTo("token1");
            assertThat(decrypted2).isEqualTo("token2");
            assertThat(decrypted1).isNotEqualTo(decrypted2);
        }
    }
}
