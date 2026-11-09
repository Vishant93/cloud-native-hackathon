package com.example.vishant.cloud_native_hackathon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.kafka.outbox.enabled=false",
		"spring.kafka.listener.auto-startup=false"
})
class CloudNativeHackathonApplicationTests {

	@Test
	void contextLoads() {
	}

}
