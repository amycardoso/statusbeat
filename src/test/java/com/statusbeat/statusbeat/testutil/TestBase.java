package com.statusbeat.statusbeat.testutil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

@ExtendWith(MockitoExtension.class)
public abstract class TestBase {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    @BeforeEach
    void setUpBase() {
    }

    protected String testUserId() {
        return "test-user-id-" + COUNTER.getAndIncrement();
    }

    protected String testSlackUserId() {
        return "U" + COUNTER.getAndIncrement();
    }

    protected String testSlackTeamId() {
        return "T" + COUNTER.getAndIncrement();
    }
}
