package com.statusbeat.statusbeat.unit.util;

import com.statusbeat.statusbeat.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EncryptionUtil")
class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;
    private static final String TEST_SECRET_KEY = "test-secret-key-for-encryption";

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKeyString", TEST_SECRET_KEY);
    }

    @Nested
    @DisplayName("encrypt")
    class EncryptTests {

        @Test
        @DisplayName("should encrypt plain text successfully")
        void shouldEncryptPlainText() {
            String plainText = "test-access-token-12345";

            String encrypted = encryptionUtil.encrypt(plainText);

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotEqualTo(plainText);
            assertThat(encrypted).isNotBlank();
        }

        @Test
        @DisplayName("should produce Base64 encoded output")
        void shouldProduceBase64EncodedOutput() {
            String plainText = "my-secret-token";

            String encrypted = encryptionUtil.encrypt(plainText);

            assertThatCode(() -> Base64.getDecoder().decode(encrypted))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should produce different ciphertext for same input due to random IV")
        void shouldProduceDifferentCiphertextForSameInput() {
            String plainText = "same-input-text";

            String encrypted1 = encryptionUtil.encrypt(plainText);
            String encrypted2 = encryptionUtil.encrypt(plainText);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("should produce output with IV and auth tag (GCM format)")
        void shouldProduceOutputWithIvAndAuthTag() {
            String plainText = "x"; // Minimal input

            String encrypted = encryptionUtil.encrypt(plainText);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // GCM format: 12-byte IV + ciphertext (1 byte) + 16-byte auth tag = 29 bytes minimum
            assertThat(decoded.length).isGreaterThanOrEqualTo(29);
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            String encrypted = encryptionUtil.encrypt("");

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotBlank();
        }

        @Test
        @DisplayName("should handle special characters")
        void shouldHandleSpecialCharacters() {
            String plainText = "token!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

            String encrypted = encryptionUtil.encrypt(plainText);

            assertThat(encrypted).isNotNull();
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            String plainText = "token-\u4e2d\u6587-\u65e5\u672c\u8a9e-\ud83c\udfb5";

            String encrypted = encryptionUtil.encrypt(plainText);

            assertThat(encrypted).isNotNull();
        }

        @Test
        @DisplayName("should handle long input")
        void shouldHandleLongInput() {
            String plainText = "x".repeat(10000);

            String encrypted = encryptionUtil.encrypt(plainText);

            assertThat(encrypted).isNotNull();
        }
    }

    @Nested
    @DisplayName("decrypt")
    class DecryptTests {

        @Test
        @DisplayName("should decrypt encrypted text successfully")
        void shouldDecryptEncryptedText() {
            String originalText = "my-spotify-access-token-xyz123";

            String encrypted = encryptionUtil.encrypt(originalText);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalText);
        }

        @Test
        @DisplayName("should decrypt empty string encryption")
        void shouldDecryptEmptyString() {
            String originalText = "";

            String encrypted = encryptionUtil.encrypt(originalText);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalText);
        }

        @Test
        @DisplayName("should decrypt special characters correctly")
        void shouldDecryptSpecialCharacters() {
            String originalText = "token!@#$%^&*()_+-=[]{}|;':\",./<>?";

            String encrypted = encryptionUtil.encrypt(originalText);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalText);
        }

        @Test
        @DisplayName("should decrypt unicode characters correctly")
        void shouldDecryptUnicodeCharacters() {
            String originalText = "token-\u4e2d\u6587-\ud83c\udfb5";

            String encrypted = encryptionUtil.encrypt(originalText);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalText);
        }

        @Test
        @DisplayName("should decrypt long text correctly")
        void shouldDecryptLongText() {
            String originalText = "abc123-".repeat(1000);

            String encrypted = encryptionUtil.encrypt(originalText);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalText);
        }

        @Test
        @DisplayName("should throw exception for invalid base64 input")
        void shouldThrowExceptionForInvalidBase64() {
            String invalidBase64 = "not-valid-base64!!!";

            assertThatThrownBy(() -> encryptionUtil.decrypt(invalidBase64))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("should throw exception for tampered ciphertext")
        void shouldThrowExceptionForTamperedCiphertext() {
            String originalText = "my-secret-token";
            String encrypted = encryptionUtil.encrypt(originalText);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            decoded[decoded.length - 1] ^= 0xFF;
            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("should throw exception for truncated ciphertext")
        void shouldThrowExceptionForTruncatedCiphertext() {
            String originalText = "my-secret-token";
            String encrypted = encryptionUtil.encrypt(originalText);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] truncated = new byte[10];
            System.arraycopy(decoded, 0, truncated, 0, 10);
            String truncatedBase64 = Base64.getEncoder().encodeToString(truncated);

            assertThatThrownBy(() -> encryptionUtil.decrypt(truncatedBase64))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("should maintain data integrity through multiple round-trips")
        void shouldMaintainDataIntegrity() {
            String[] testInputs = {
                    "simple-token",
                    "BQDnV0x1234567890abcdefghijklmnop",
                    "AQA-refresh-token-with-special-chars!@#",
                    "",
                    "x",
                    "a".repeat(500)
            };

            for (String originalText : testInputs) {
                String encrypted = encryptionUtil.encrypt(originalText);
                String decrypted = encryptionUtil.decrypt(encrypted);

                assertThat(decrypted)
                        .as("Round-trip for: " + (originalText.length() > 20 ?
                                originalText.substring(0, 20) + "..." : originalText))
                        .isEqualTo(originalText);
            }
        }

        @Test
        @DisplayName("should generate unique IVs for each encryption")
        void shouldGenerateUniqueIVs() {
            String plainText = "same-input";
            Set<String> encryptedSet = new HashSet<>();

            for (int i = 0; i < 100; i++) {
                String encrypted = encryptionUtil.encrypt(plainText);
                encryptedSet.add(encrypted);
            }
            assertThat(encryptedSet).hasSize(100);
        }
    }

    @Nested
    @DisplayName("with different secret keys")
    class DifferentKeysTests {

        @Test
        @DisplayName("should not decrypt with different key")
        void shouldNotDecryptWithDifferentKey() {
            String originalText = "my-secret-token";
            String encrypted = encryptionUtil.encrypt(originalText);

            // Create new instance with different key
            EncryptionUtil differentKeyUtil = new EncryptionUtil();
            ReflectionTestUtils.setField(differentKeyUtil, "secretKeyString", "different-secret-key");

            assertThatThrownBy(() -> differentKeyUtil.decrypt(encrypted))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }
    }
}
