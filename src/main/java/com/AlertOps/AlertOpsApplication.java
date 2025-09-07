package com.AlertOps;

//https://chatgpt.com/c/68b9b0d0-7804-8322-8ca7-329e5dbaa722

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlertOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertOpsApplication.class, args);
	}

}

/**
 * SpringApplication :
 *   - Class that actually launches the Spring Boot app.
 *   - Creates the ApplicationContext (IoC container) depending on environment:
 *        • Web app -> AnnotationConfigServletWebServerApplicationContext
 *        • Non-web -> AnnotationConfigApplicationContext
 *   - Handles startup process:
 *        • Classpath scanning & bean registration
 *        • Loads application.properties / application.yml
 *        • Starts embedded server (e.g., Tomcat/Jetty)
 *        • Publishes lifecycle events(eg: ApplicationStartedEvent, ApplicationReadyEvent.)
 *
 * SpringBootApplication :
 *   - Meta-annotation used on main class (blueprint for context).
 *   - Combines three key annotations:
 *        • @Configuration -> Defines beans in code
 *        • @EnableAutoConfiguration -> Auto-configures beans based on classpath & settings
 *        • @ComponentScan -> Scans package for @Component, @Service, @Controller, etc.
 *
 * 👉 Difference:
 *   - @SpringBootApplication = tells Spring *what to build*
 *   - SpringApplication.run() = actually *builds and runs it*
 */
