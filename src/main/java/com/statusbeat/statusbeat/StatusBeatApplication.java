package com.statusbeat.statusbeat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.retry.annotation.EnableRetry;

// Scheduling is enabled in SchedulerConfig, behind statusbeat.sync.enabled
@SpringBootApplication
@EnableRetry
@ServletComponentScan
public class StatusBeatApplication {

	public static void main(String[] args) {
		SpringApplication.run(StatusBeatApplication.class, args);
	}

}
