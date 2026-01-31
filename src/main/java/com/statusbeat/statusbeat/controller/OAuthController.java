package com.statusbeat.statusbeat.controller;

import com.statusbeat.statusbeat.constants.AppConstants;
import com.statusbeat.statusbeat.service.SpotifyService;
import com.statusbeat.statusbeat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.net.URI;

@Slf4j
@Controller
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final UserService userService;
    private final SpotifyService spotifyService;

    @GetMapping("/spotify")
    public RedirectView initiateSpotifyOAuth(@RequestParam("userId") String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Spotify OAuth initiated without userId");
            return new RedirectView(AppConstants.ERROR_PATH + "?message=" + AppConstants.ERROR_PARAM_INVALID_USER);
        }

        log.info("Initiating Spotify OAuth flow for user: {}", userId);

        URI authUri = spotifyService.getAuthorizationUri();
        String redirectUrl = authUri.toString() + "&state=" + userId;

        return new RedirectView(redirectUrl);
    }

    @GetMapping("/spotify/callback")
    public String handleSpotifyCallback(@RequestParam("code") String code,
                                        @RequestParam(value = "state", required = false) String userId,
                                        @RequestParam(value = "error", required = false) String error) {
        try {
            if (error != null) {
                log.error("Spotify OAuth error: {}", error);
                return "redirect:" + AppConstants.ERROR_PATH + "?message=" + AppConstants.ERROR_PARAM_SPOTIFY_DENIED;
            }

            if (userId == null || userId.trim().isEmpty()) {
                log.error("Spotify OAuth callback received without userId");
                return "redirect:" + AppConstants.ERROR_PATH + "?message=" + AppConstants.ERROR_PARAM_INVALID_USER;
            }

            log.info("Received Spotify OAuth callback for user: {}", userId);

            // Exchange code for access token
            AuthorizationCodeCredentials credentials = spotifyService.getAccessToken(code);

            // Generate temporary Spotify user ID (will be updated on first sync)
            String spotifyUserId = AppConstants.SPOTIFY_USER_ID_PREFIX + System.currentTimeMillis();

            // Update user with Spotify tokens
            userService.updateSpotifyTokens(
                    userId,
                    spotifyUserId,
                    credentials.getAccessToken(),
                    credentials.getRefreshToken(),
                    credentials.getExpiresIn()
            );

            log.info("Successfully authenticated Spotify for user: {}", userId);

            return "redirect:" + AppConstants.SUCCESS_PATH;

        } catch (Exception e) {
            log.error("Error handling Spotify OAuth callback", e);
            return "redirect:" + AppConstants.ERROR_PATH + "?message=" + AppConstants.ERROR_PARAM_SPOTIFY_ERROR;
        }
    }
}
