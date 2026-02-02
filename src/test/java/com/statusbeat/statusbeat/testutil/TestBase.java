package com.statusbeat.statusbeat.testutil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class TestBase {

    @BeforeEach
    void setUpBase() {
    }

    protected String testUserId() {
        return "test-user-id-" + System.nanoTime();
    }

    protected String testSlackUserId() {
        return "U" + System.nanoTime();
    }

    protected String testSlackTeamId() {
        return "T" + System.nanoTime();
    }
}
