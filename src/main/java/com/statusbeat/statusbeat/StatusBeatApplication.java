package com.statusbeat.statusbeat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
@ServletComponentScan
public class StatusBeatApplication {

	public static void main(String[] args) {
		SpringApplication.run(StatusBeatApplication.class, args);
	}

}
