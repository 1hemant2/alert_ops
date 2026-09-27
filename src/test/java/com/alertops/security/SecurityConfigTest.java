package com.alertops.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void corsUsesConfiguredOriginsAndAllowsBearerApiRequests() {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("http://localhost:5173, https://alerts.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/task");

        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertEquals(2, cors.getAllowedOrigins().size());
        assertTrue(cors.getAllowedOrigins().contains("https://alerts.example.com"));
        assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertTrue(cors.getAllowedHeaders().contains("Authorization"));
        assertFalse(cors.getAllowCredentials());
    }
}
