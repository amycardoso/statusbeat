package com.statusbeat.statusbeat.security;

import com.statusbeat.statusbeat.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Encryption Security Tests")
class EncryptionSecurityTest {

    private EncryptionUtil encryptionUtil;
    private static final String SECRET_KEY = "production-grade-secret-key-32chars";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BYTES = 16;

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKeyString", SECRET_KEY);
    }

    @Nested
    @DisplayName("AES-256-GCM Algorithm Verification")
    class Aes256GcmVerificationTests {

        @Test
        @DisplayName("should use AES-256-GCM with proper key length")
        void shouldUseAes256GcmWithProperKeyLength() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);

            assertThat(encrypted).isNotEqualTo(testData);
            String decrypted = encryptionUtil.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(testData);
        }

        @Test
        @DisplayName("should produce encrypted output with IV prefix and auth tag")
        void shouldProduceEncryptedOutputWithIvPrefixAndAuthTag() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // GCM output: 12-byte IV + ciphertext + 16-byte auth tag
            int expectedMinLength = GCM_IV_LENGTH + testData.length() + GCM_TAG_LENGTH_BYTES;
            assertThat(decoded.length).isGreaterThanOrEqualTo(expectedMinLength);
        }

        @Test
        @DisplayName("should handle variable length inputs without padding")
        void shouldHandleVariableLengthInputsWithoutPadding() {
            for (int len = 1; len <= 32; len++) {
                String testData = "x".repeat(len);
                String encrypted = encryptionUtil.encrypt(testData);
                String decrypted = encryptionUtil.decrypt(encrypted);

                assertThat(decrypted).isEqualTo(testData);
            }
        }
    }

    @Nested
    @DisplayName("IV (Initialization Vector) Security")
    class IvSecurityTests {

        @Test
        @DisplayName("should generate unique IV for each encryption")
        void shouldGenerateUniqueIvForEachEncryption() {
            String testData = "same-data";
            Set<String> encryptedResults = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                encryptedResults.add(encryptionUtil.encrypt(testData));
            }
            assertThat(encryptedResults).hasSize(1000);
        }

        @Test
        @DisplayName("should use cryptographically secure random 12-byte IV")
        void shouldUseCryptographicallySecureRandom12ByteIv() {
            String testData = "test-data";
            Set<String> ivSet = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                String encrypted = encryptionUtil.encrypt(testData);
                byte[] decoded = Base64.getDecoder().decode(encrypted);
                byte[] iv = new byte[GCM_IV_LENGTH];
                System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
                ivSet.add(Base64.getEncoder().encodeToString(iv));
            }
            assertThat(ivSet).hasSize(100);
        }

        @Test
        @DisplayName("should fail decryption with corrupted IV")
        void shouldFailDecryptionWithCorruptedIv() {
            String testData = "test-data";
            String encrypted = encryptionUtil.encrypt(testData);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            decoded[0] ^= 0xFF;
            String corrupted = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> encryptionUtil.decrypt(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Key Derivation Security")
    class KeyDerivationTests {

        @Test
        @DisplayName("should encrypt and decrypt successfully with PBKDF2 derived key")
        void shouldEncryptAndDecryptSuccessfully() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(testData);
        }

        @Test
        @DisplayName("should produce different ciphertext with different keys")
        void shouldProduceDifferentCiphertextWithDifferentKeys() {
            String testData = "test-token";
            String encrypted1 = encryptionUtil.encrypt(testData);

            EncryptionUtil otherUtil = new EncryptionUtil();
            ReflectionTestUtils.setField(otherUtil, "secretKeyString", "different-secret-key");

            String encrypted2 = otherUtil.encrypt(testData);
            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("should not decrypt with wrong key")
        void shouldNotDecryptWithWrongKey() {
            String testData = "sensitive-token";
            String encrypted = encryptionUtil.encrypt(testData);

            EncryptionUtil wrongKeyUtil = new EncryptionUtil();
            ReflectionTestUtils.setField(wrongKeyUtil, "secretKeyString", "wrong-key-attempt");

            assertThatThrownBy(() -> wrongKeyUtil.decrypt(encrypted))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }
    }

    @Nested
    @DisplayName("Authenticated Encryption (GCM Tag)")
    class AuthenticatedEncryptionTests {

        @Test
        @DisplayName("should detect bit flip in ciphertext via auth tag")
        void shouldDetectBitFlipInCiphertextViaAuthTag() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // Flip a bit in the ciphertext portion (after IV)
            decoded[GCM_IV_LENGTH + 2] ^= 0x01;

            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should detect auth tag tampering")
        void shouldDetectAuthTagTampering() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // Flip a bit in the auth tag (last 16 bytes)
            decoded[decoded.length - 1] ^= 0x01;

            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should detect truncation")
        void shouldDetectTruncation() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] truncated = new byte[decoded.length - 5];
            System.arraycopy(decoded, 0, truncated, 0, truncated.length);

            String tampered = Base64.getEncoder().encodeToString(truncated);

            assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should detect extension")
        void shouldDetectExtension() {
            String testData = "test-token";
            String encrypted = encryptionUtil.encrypt(testData);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] extended = new byte[decoded.length + 5];
            System.arraycopy(decoded, 0, extended, 0, decoded.length);

            String tampered = Base64.getEncoder().encodeToString(extended);

            assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should detect invalid base64")
        void shouldDetectInvalidBase64() {
            assertThatThrownBy(() -> encryptionUtil.decrypt("not-valid-base64!!!"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }
    }

    @Nested
    @DisplayName("Token Protection")
    class TokenProtectionTests {

        @Test
        @DisplayName("should safely encrypt Spotify access token")
        void shouldSafelyEncryptSpotifyAccessToken() {
            String spotifyToken = "BQDnV0x1234567890abcdefghijklmnopqrstuvwxyz";
            String encrypted = encryptionUtil.encrypt(spotifyToken);

            assertThat(encrypted).doesNotContain("BQD");
            String decrypted = encryptionUtil.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(spotifyToken);
        }

        @Test
        @DisplayName("should safely encrypt Spotify refresh token")
        void shouldSafelyEncryptSpotifyRefreshToken() {
            String refreshToken = "AQArefresh1234567890abcdefghijklmnopqrstuvwxyz";
            String encrypted = encryptionUtil.encrypt(refreshToken);

            assertThat(encrypted).doesNotContain("AQA");

            String decrypted = encryptionUtil.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(refreshToken);
        }

        @Test
        @DisplayName("should handle very long tokens")
        void shouldHandleVeryLongTokens() {
            String longToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
                    "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QiLCJpYXQiOjE1MTYyMzkwMjJ9." +
                    "signature_here_would_be_very_long_in_real_life_".repeat(10);

            String encrypted = encryptionUtil.encrypt(longToken);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(longToken);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should not leak information in error messages")
        void shouldNotLeakInformationInErrorMessages() {
            String testData = "secret-token-xyz";
            String encrypted = encryptionUtil.encrypt(testData);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            decoded[decoded.length - 1] ^= 0xFF;
            String corrupted = Base64.getEncoder().encodeToString(decoded);

            try {
                encryptionUtil.decrypt(corrupted);
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).doesNotContain("secret-token");
                assertThat(e.getMessage()).doesNotContain(testData);
            }
        }

        @Test
        @DisplayName("should handle empty input gracefully")
        void shouldHandleEmptyInputGracefully() {
            String encrypted = encryptionUtil.encrypt("");
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEmpty();
        }
    }
}
