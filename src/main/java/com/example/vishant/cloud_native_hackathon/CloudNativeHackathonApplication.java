package com.example.vishant.cloud_native_hackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudNativeHackathonApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudNativeHackathonApplication.class, args);
	}

}
