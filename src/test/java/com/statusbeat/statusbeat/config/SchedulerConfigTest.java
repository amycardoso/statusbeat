package com.statusbeat.statusbeat.config;

import com.statusbeat.statusbeat.testutil.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scheduler Config Tests")
class SchedulerConfigTest {

    /**
     * Tests drive syncMusicStatus() directly. A background run in the same
     * context would race them and call the mocks mid-test.
     */
    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @Import(TestConfig.class)
    @DisplayName("Test profile")
    class TestProfileTests {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("should not register any scheduled task")
        void shouldNotRegisterScheduledTasks() {
            assertThat(scheduledTasks(context)).isEmpty();
        }
    }

    @Nested
    @DisplayName("statusbeat.sync.enabled")
    class SyncEnabledPropertyTests {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(SchedulerConfig.SchedulingConfig.class, PollingJob.class);

        @Test
        @DisplayName("should schedule jobs by default")
        void shouldScheduleByDefault() {
            contextRunner.run(context -> assertThat(scheduledTasks(context)).hasSize(1));
        }

        @Test
        @DisplayName("should schedule jobs when true")
        void shouldScheduleWhenEnabled() {
            contextRunner.withPropertyValues("statusbeat.sync.enabled=true")
                    .run(context -> assertThat(scheduledTasks(context)).hasSize(1));
        }

        @Test
        @DisplayName("should not schedule jobs when false")
        void shouldNotScheduleWhenDisabled() {
            contextRunner.withPropertyValues("statusbeat.sync.enabled=false")
                    .run(context -> assertThat(scheduledTasks(context)).isEmpty());
        }
    }

    private static List<ScheduledTask> scheduledTasks(ApplicationContext context) {
        return context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .map(ScheduledTaskHolder::getScheduledTasks)
                .flatMap(Collection::stream)
                .toList();
    }

    static class PollingJob {

        @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
        void poll() {
        }
    }
}
