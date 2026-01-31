package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.config.SpotifyConfig;
import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.exception.*;
import com.statusbeat.statusbeat.model.CurrentlyPlayingTrackInfo;
import com.statusbeat.statusbeat.model.SpotifyDevice;
import com.statusbeat.statusbeat.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ParseException;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyService {

    private final SpotifyConfig spotifyConfig;
    private final UserService userService;
    private final TokenValidationService tokenValidationService;

    public URI getAuthorizationUri() {
        SpotifyApi spotifyApi = getSpotifyApi(null);

        AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi.authorizationCodeUri()
                .scope(spotifyConfig.getScope())
                .show_dialog(true)
                .build();

        return authorizationCodeUriRequest.execute();
    }

    public AuthorizationCodeCredentials getAccessToken(String code) throws IOException, ParseException, SpotifyWebApiException {
        SpotifyApi spotifyApi = getSpotifyApi(null);

        AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(code).build();
        return authorizationCodeRequest.execute();
    }

    public AuthorizationCodeCredentials refreshAccessToken(String refreshToken) throws IOException, ParseException, SpotifyWebApiException {
        SpotifyApi spotifyApi = SpotifyApi.builder()
                .setClientId(spotifyConfig.getClientId())
                .setClientSecret(spotifyConfig.getClientSecret())
                .setRefreshToken(refreshToken)
                .build();

        AuthorizationCodeRefreshRequest refreshRequest = spotifyApi.authorizationCodeRefresh().build();
        return refreshRequest.execute();
    }

    private User ensureValidToken(User user) throws IOException, ParseException, SpotifyWebApiException {
        if (userService.isSpotifyTokenExpired(user)) {
            log.debug("Spotify token expired or expiring soon for user {}, refreshing...", user.getSlackUserId());
            refreshUserToken(user);
            return userService.findBySlackUserId(user.getSlackUserId())
                    .orElseThrow(() -> new RuntimeException("User not found after token refresh"));
        }
        return user;
    }

    public List<SpotifyDevice> getAvailableDevices(User user) {
        try {
            user = ensureValidToken(user);

            String accessToken = userService.getDecryptedSpotifyAccessToken(user);
            SpotifyApi spotifyApi = getSpotifyApi(accessToken);

            var request = spotifyApi.getUsersAvailableDevices().build();
            var devices = request.execute();

            if (devices == null || devices.length == 0) {
                log.debug("No devices found for user {}", user.getId());
                return new ArrayList<>();
            }

            return Arrays.stream(devices)
                    .map(device -> SpotifyDevice.builder()
                            .id(device.getId())
                            .name(device.getName())
                            .type(device.getType())
                            .isActive(device.getIs_active())
                            .build())
                    .collect(Collectors.toList());

        } catch (UnauthorizedException e) {
            log.warn("Unauthorized error for user {}: {}", user.getSlackUserId(), e.getMessage());
            handleSpotifyTokenError(user, e.getMessage());
            return new ArrayList<>();
        } catch (SpotifyWebApiException e) {
            String errorMsg = e.getMessage();
            if (tokenValidationService.isSpotifyTokenInvalidError(errorMsg)) {
                log.warn("Token invalidation detected for user {}: {}", user.getSlackUserId(), errorMsg);
                handleSpotifyTokenError(user, errorMsg);
            } else {
                log.error("Spotify API error for user {}: {}", user.getSlackUserId(), errorMsg);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching devices for user {}", user.getId(), e);
            return new ArrayList<>();
        }
    }

    public CurrentlyPlayingTrackInfo getCurrentlyPlayingTrack(User user) {
        try {
            user = ensureValidToken(user);

            String accessToken = userService.getDecryptedSpotifyAccessToken(user);
            SpotifyApi spotifyApi = getSpotifyApi(accessToken);

            var contextRequest = spotifyApi.getInformationAboutUsersCurrentPlayback().build();
            CurrentlyPlayingContext context = contextRequest.execute();

            if (context == null || context.getItem() == null || !context.getIs_playing()) {
                log.debug("No track currently playing for user {}", user.getId());
                return null;
            }

            if (context.getItem() instanceof Track) {
                Track track = (Track) context.getItem();
                String artistName = track.getArtists().length > 0 ? track.getArtists()[0].getName() : AppConstants.UNKNOWN_ARTIST;

                String deviceId = context.getDevice() != null ? context.getDevice().getId() : null;
                String deviceName = context.getDevice() != null ? context.getDevice().getName() : null;

                return CurrentlyPlayingTrackInfo.builder()
                        .trackId(track.getId())
                        .trackName(track.getName())
                        .artistName(artistName)
                        .isPlaying(context.getIs_playing())
                        .durationMs(track.getDurationMs())
                        .progressMs(context.getProgress_ms())
                        .deviceId(deviceId)
                        .deviceName(deviceName)
                        .build();
            }

            return null;
        } catch (UnauthorizedException e) {
            log.warn("Unauthorized error for user {}: {}", user.getSlackUserId(), e.getMessage());
            handleSpotifyTokenError(user, e.getMessage());
            return null;
        } catch (SpotifyWebApiException e) {
            String errorMsg = e.getMessage();
            if (tokenValidationService.isSpotifyTokenInvalidError(errorMsg)) {
                log.warn("Token invalidation detected for user {}: {}", user.getSlackUserId(), errorMsg);
                handleSpotifyTokenError(user, errorMsg);
            } else {
                log.error("Spotify API error for user {}: {}", user.getSlackUserId(), errorMsg);
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching currently playing track for user {}", user.getId(), e);
            return null;
        }
    }

    private void handleSpotifyTokenError(User user, String errorMessage) {
        tokenValidationService.markUserAsInvalidated(user, errorMessage);
        log.warn("Spotify token invalidated for user {}. User should be notified to reconnect.",
                user.getSlackUserId());
    }

    public void pausePlayback(User user) {
        try {
            final User refreshedUser = ensureValidToken(user);

            executePlayerCommand(refreshedUser, "pause", () -> {
                String accessToken = userService.getDecryptedSpotifyAccessToken(refreshedUser);
                SpotifyApi spotifyApi = getSpotifyApi(accessToken);
                spotifyApi.pauseUsersPlayback().build().execute();
            });
        } catch (IOException | ParseException | SpotifyWebApiException e) {
            log.error("Failed to refresh token for user {}", user.getSlackUserId(), e);
            throw new SpotifyTokenExpiredException();
        }
    }

    public void resumePlayback(User user) {
        try {
            final User refreshedUser = ensureValidToken(user);

            executePlayerCommand(refreshedUser, "resume", () -> {
                String accessToken = userService.getDecryptedSpotifyAccessToken(refreshedUser);
                SpotifyApi spotifyApi = getSpotifyApi(accessToken);
                spotifyApi.startResumeUsersPlayback().build().execute();
            });
        } catch (IOException | ParseException | SpotifyWebApiException e) {
            log.error("Failed to refresh token for user {}", user.getSlackUserId(), e);
            throw new SpotifyTokenExpiredException();
        }
    }

    private void executePlayerCommand(User user, String operation, PlayerCommand command) {
        try {
            command.execute();
            log.info("{}d playback for user {}", operation, user.getSlackUserId());
        } catch (NotFoundException e) {
            log.warn("No active device found for user {}", user.getSlackUserId());
            throw new NoActiveDeviceException();
        } catch (UnauthorizedException e) {
            log.warn("Unauthorized - token expired for user {}", user.getSlackUserId());
            throw new SpotifyTokenExpiredException();
        } catch (ForbiddenException e) {
            log.warn("Forbidden - Premium required for user {}", user.getSlackUserId());
            throw new SpotifyPremiumRequiredException();
        } catch (TooManyRequestsException e) {
            log.warn("Rate limited for user {}", user.getSlackUserId());
            throw new SpotifyRateLimitException();
        } catch (SpotifyWebApiException e) {
            log.error("Spotify API error {}ing playback for user {}: {}", operation, user.getSlackUserId(), e.getMessage(), e);
            throw new SpotifyException("Spotify API error: " + e.getMessage(), e);
        } catch (IOException | ParseException e) {
            log.error("Network error {}ing playback for user {}", operation, user.getSlackUserId(), e);
            throw new SpotifyException("Network error communicating with Spotify", e);
        }
    }

    @FunctionalInterface
    private interface PlayerCommand {
        void execute() throws IOException, ParseException, SpotifyWebApiException;
    }

    private void refreshUserToken(User user) throws IOException, ParseException, SpotifyWebApiException {
        try {
            log.info("Refreshing Spotify token for user {}", user.getSlackUserId());
            String refreshToken = userService.getDecryptedSpotifyRefreshToken(user);
            AuthorizationCodeCredentials credentials = refreshAccessToken(refreshToken);

            userService.updateSpotifyTokens(
                    user.getId(),
                    user.getSpotifyUserId(),
                    credentials.getAccessToken(),
                    credentials.getRefreshToken() != null ? credentials.getRefreshToken() : refreshToken,
                    credentials.getExpiresIn()
            );
            log.info("Successfully refreshed Spotify token for user {}. New token expires in {} seconds",
                    user.getSlackUserId(), credentials.getExpiresIn());
        } catch (SpotifyWebApiException e) {
            String errorMsg = e.getMessage();
            if (tokenValidationService.isSpotifyTokenInvalidError(errorMsg)) {
                log.error("Token refresh failed - token has been revoked for user {}: {}",
                        user.getSlackUserId(), errorMsg);
                handleSpotifyTokenError(user, errorMsg);
                throw e;
            } else {
                log.error("Failed to refresh Spotify token for user {}: {}",
                        user.getSlackUserId(), errorMsg);
                throw e;
            }
        }
    }

    private SpotifyApi getSpotifyApi(String accessToken) {
        SpotifyApi.Builder builder = SpotifyApi.builder()
                .setClientId(spotifyConfig.getClientId())
                .setClientSecret(spotifyConfig.getClientSecret())
                .setRedirectUri(URI.create(spotifyConfig.getRedirectUri()));

        if (accessToken != null) {
            builder.setAccessToken(accessToken);
        }

        return builder.build();
    }
}
