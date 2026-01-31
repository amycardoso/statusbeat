package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validating and detecting token invalidation errors.
 * Follows Single Responsibility Principle: only detects errors and marks users as invalidated.
 * Notification handling is delegated to the calling service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenValidationService {

    private final UserService userService;

    /**
     * Checks if an error message indicates a revoked or invalid Slack token.
     * Based on SpotMyStatus pattern: detects "invalid_auth", "token_revoked", "account_inactive"
     *
     * @param errorMessage The error message from Slack API
     * @return true if the error indicates an invalid token
     */
    public boolean isSlackTokenInvalidError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerError = errorMessage.toLowerCase();
        return lowerError.contains("invalid_auth")
                || lowerError.contains("token_revoked")
                || lowerError.contains("account_inactive")
                || lowerError.contains("invalid_token")
                || lowerError.contains("not_authed");
    }

    /**
     * Checks if an error message indicates a revoked or invalid Spotify token.
     * Based on SpotMyStatus pattern: detects "invalid_grant" error
     *
     * @param errorMessage The error message from Spotify API
     * @return true if the error indicates an invalid token
     */
    public boolean isSpotifyTokenInvalidError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerError = errorMessage.toLowerCase();
        return lowerError.contains("invalid_grant")
                || lowerError.contains("invalid token")
                || lowerError.contains("the access token expired");
    }

    /**
     * Marks a user as having an invalidated token.
     * This method ONLY handles the database update - no notifications.
     * The calling service is responsible for sending notifications.
     *
     * @param user The user whose token is invalid
     * @param errorMessage The error message that triggered invalidation
     */
    public void markUserAsInvalidated(User user, String errorMessage) {
        log.error("Token invalidated for user {}: {}", user.getSlackUserId(), errorMessage);

        // Mark user as having invalid token
        userService.setTokenInvalidated(user.getId(), true);

        log.info("User {} marked as token invalidated. Sync disabled.", user.getSlackUserId());
    }

    /**
     * Checks if a user's token has been invalidated.
     * Used to skip sync operations for invalidated users.
     */
    public boolean isUserTokenInvalidated(User user) {
        return user.isTokenInvalidated();
    }

    /**
     * Reactivates a user after token reauthorization.
     * Called after user successfully completes OAuth again.
     */
    public void reactivateUser(String userId) {
        userService.setTokenInvalidated(userId, false);
        log.info("User {} reactivated after token reauthorization", userId);
    }
}
