package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.service.TokenValidationService;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("TokenValidationService")
class TokenValidationServiceTest extends TestBase {

    @Mock
    private UserService userService;

    private TokenValidationService tokenValidationService;

    @BeforeEach
    void setUp() {
        tokenValidationService = new TokenValidationService(userService);
    }

    @Nested
    @DisplayName("isSlackTokenInvalidError")
    class IsSlackTokenInvalidErrorTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "invalid_auth",
                "INVALID_AUTH",
                "Error: invalid_auth",
                "token_revoked",
                "Token_Revoked",
                "account_inactive",
                "ACCOUNT_INACTIVE",
                "invalid_token",
                "not_authed",
                "NOT_AUTHED"
        })
        @DisplayName("should return true for Slack token invalidation errors")
        void shouldReturnTrueForInvalidErrors(String errorMessage) {
            boolean result = tokenValidationService.isSlackTokenInvalidError(errorMessage);

            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "rate_limited",
                "channel_not_found",
                "user_not_found",
                "network_error",
                "timeout",
                "internal_error"
        })
        @DisplayName("should return false for non-token errors")
        void shouldReturnFalseForNonTokenErrors(String errorMessage) {
            boolean result = tokenValidationService.isSlackTokenInvalidError(errorMessage);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null error message")
        void shouldReturnFalseForNullError() {
            boolean result = tokenValidationService.isSlackTokenInvalidError(null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for empty error message")
        void shouldReturnFalseForEmptyError() {
            boolean result = tokenValidationService.isSlackTokenInvalidError("");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isSpotifyTokenInvalidError")
    class IsSpotifyTokenInvalidErrorTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "invalid_grant",
                "INVALID_GRANT",
                "Error: invalid_grant",
                "invalid token",
                "Invalid Token",
                "The access token expired",
                "THE ACCESS TOKEN EXPIRED"
        })
        @DisplayName("should return true for Spotify token invalidation errors")
        void shouldReturnTrueForInvalidErrors(String errorMessage) {
            boolean result = tokenValidationService.isSpotifyTokenInvalidError(errorMessage);

            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "rate_limit",
                "premium_required",
                "no_active_device",
                "network_error",
                "forbidden",
                "service_unavailable"
        })
        @DisplayName("should return false for non-token errors")
        void shouldReturnFalseForNonTokenErrors(String errorMessage) {
            boolean result = tokenValidationService.isSpotifyTokenInvalidError(errorMessage);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null error message")
        void shouldReturnFalseForNullError() {
            boolean result = tokenValidationService.isSpotifyTokenInvalidError(null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for empty error message")
        void shouldReturnFalseForEmptyError() {
            boolean result = tokenValidationService.isSpotifyTokenInvalidError("");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("markUserAsInvalidated")
    class MarkUserAsInvalidatedTests {

        @Test
        @DisplayName("should call userService to set token invalidated")
        void shouldCallUserServiceToSetTokenInvalidated() {
            User user = TestDataFactory.createUserWithSpotify();

            tokenValidationService.markUserAsInvalidated(user, "invalid_grant");

            verify(userService).setTokenInvalidated(user.getId(), true);
        }

        @Test
        @DisplayName("should log error message")
        void shouldCallUserServiceWithUserId() {
            User user = TestDataFactory.createUserWithSpotify();
            String errorMessage = "token_revoked: User uninstalled app";

            tokenValidationService.markUserAsInvalidated(user, errorMessage);

            verify(userService, times(1)).setTokenInvalidated(user.getId(), true);
        }
    }

    @Nested
    @DisplayName("isUserTokenInvalidated")
    class IsUserTokenInvalidatedTests {

        @Test
        @DisplayName("should return true when user token is invalidated")
        void shouldReturnTrueWhenInvalidated() {
            User user = TestDataFactory.createInvalidatedUser();

            boolean result = tokenValidationService.isUserTokenInvalidated(user);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when user token is valid")
        void shouldReturnFalseWhenValid() {
            User user = TestDataFactory.createUserWithSpotify();

            boolean result = tokenValidationService.isUserTokenInvalidated(user);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("reactivateUser")
    class ReactivateUserTests {

        @Test
        @DisplayName("should call userService to clear invalidation")
        void shouldCallUserServiceToClearInvalidation() {
            String userId = "user-123";

            tokenValidationService.reactivateUser(userId);

            verify(userService).setTokenInvalidated(userId, false);
        }
    }
}
