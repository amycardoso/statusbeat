package com.statusbeat.statusbeat.testutil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for unit tests.
 * Provides common test setup and utilities for mocking.
 */
@ExtendWith(MockitoExtension.class)
public abstract class TestBase {

    @BeforeEach
    void setUpBase() {
        // Common setup for all unit tests can be added here
    }

    /**
     * Helper method to create a test user ID.
     */
    protected String testUserId() {
        return "test-user-id-" + System.nanoTime();
    }

    /**
     * Helper method to create a test Slack user ID.
     */
    protected String testSlackUserId() {
        return "U" + System.nanoTime();
    }

    /**
     * Helper method to create a test Slack team ID.
     */
    protected String testSlackTeamId() {
        return "T" + System.nanoTime();
    }
}
