package com.alertops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AlertOpsApplication {
	public static void main(String[] args) {
		SpringApplication.run(AlertOpsApplication.class, args);
	}
}
