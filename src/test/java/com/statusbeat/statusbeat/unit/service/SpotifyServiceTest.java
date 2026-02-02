package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.config.SpotifyConfig;
import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.service.SpotifyService;
import com.statusbeat.statusbeat.service.TokenValidationService;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SpotifyService")
@ExtendWith(MockitoExtension.class)
class SpotifyServiceTest {

    @Mock
    private SpotifyConfig spotifyConfig;

    @Mock
    private UserService userService;

    @Mock
    private TokenValidationService tokenValidationService;

    private SpotifyService spotifyService;

    @BeforeEach
    void setUp() {
        spotifyService = new SpotifyService(spotifyConfig, userService, tokenValidationService);
    }

    @Nested
    @DisplayName("getAuthorizationUri")
    class GetAuthorizationUriTests {

        @Test
        @DisplayName("should return Spotify authorization URI")
        void shouldReturnAuthorizationUri() {
            when(spotifyConfig.getClientId()).thenReturn("test-client-id");
            when(spotifyConfig.getClientSecret()).thenReturn("test-client-secret");
            when(spotifyConfig.getRedirectUri()).thenReturn("http://localhost:8080/callback/spotify");
            when(spotifyConfig.getScope()).thenReturn("user-read-currently-playing");

            var uri = spotifyService.getAuthorizationUri();

            assertThat(uri).isNotNull();
            assertThat(uri.toString()).contains("accounts.spotify.com");
            assertThat(uri.toString()).contains("authorize");
            assertThat(uri.toString()).contains("client_id=test-client-id");
        }
    }

    @Nested
    @DisplayName("getAvailableDevices")
    class GetAvailableDevicesTests {

        @Test
        @DisplayName("should return empty list when API call fails")
        void shouldReturnEmptyListWhenApiFails() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenReturn("invalid-token");

            var result = spotifyService.getAvailableDevices(user);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCurrentlyPlayingTrack")
    class GetCurrentlyPlayingTrackTests {

        @Test
        @DisplayName("should return null when API call fails")
        void shouldReturnNullWhenApiFails() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenReturn("invalid-token");

            var result = spotifyService.getCurrentlyPlayingTrack(user);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("token management")
    class TokenManagementTests {

        @Test
        @DisplayName("should check if token is expired before API calls")
        void shouldCheckTokenExpiration() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenReturn("token");

            spotifyService.getCurrentlyPlayingTrack(user);

            verify(userService).isSpotifyTokenExpired(user);
        }

        @Test
        @DisplayName("should get decrypted access token for API calls")
        void shouldGetDecryptedAccessToken() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenReturn("decrypted-token");

            spotifyService.getAvailableDevices(user);

            verify(userService).getDecryptedSpotifyAccessToken(user);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return empty list on device fetch error")
        void shouldReturnEmptyListOnDeviceFetchError() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenThrow(new RuntimeException("Error"));

            var result = spotifyService.getAvailableDevices(user);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return null on track fetch error")
        void shouldReturnNullOnTrackFetchError() {
            User user = TestDataFactory.createUserWithSpotify();
            when(userService.isSpotifyTokenExpired(user)).thenReturn(false);
            when(userService.getDecryptedSpotifyAccessToken(user)).thenThrow(new RuntimeException("Error"));

            var result = spotifyService.getCurrentlyPlayingTrack(user);

            assertThat(result).isNull();
        }
    }
}
