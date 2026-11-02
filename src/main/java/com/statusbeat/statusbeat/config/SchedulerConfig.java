package com.statusbeat.statusbeat.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Guards scheduled tasks with a MongoDB-backed lock, so that overlapping
 * instances (rolling deploys, horizontal scaling) never run the same job
 * concurrently.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(MongoTemplate mongoTemplate) {
        return new MongoLockProvider(mongoTemplate.getDb());
    }

    /**
     * Background polling can be turned off (statusbeat.sync.enabled=false),
     * e.g. in tests that drive the sync loop directly.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "statusbeat.sync.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
