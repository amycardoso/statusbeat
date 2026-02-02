package com.statusbeat.statusbeat;

import com.statusbeat.statusbeat.testutil.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class StatusBeatApplicationTests {

	@Test
	void contextLoads() {
	}

}
