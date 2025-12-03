package com.busify.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import jakarta.annotation.PostConstruct;

import java.util.TimeZone;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@OpenAPIDefinition
@EnableScheduling
@EnableCaching
public class ProjectApplication {

	public static void main(String[] args) {
		// Set default timezone for JVM
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		SpringApplication.run(ProjectApplication.class, args);
	}

	@PostConstruct
	public void init() {
		// Ensure timezone is set after Spring context is initialized
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
	}
}