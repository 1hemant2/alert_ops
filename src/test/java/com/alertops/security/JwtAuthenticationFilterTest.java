package com.alertops.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtUtil.class));

    @Test
    void skipsMainHealthEndpoint() {
        assertThat(filter.shouldNotFilter(requestFor("/actuator/health"))).isTrue();
    }

    @Test
    void skipsHealthEndpointSubpaths() {
        assertThat(filter.shouldNotFilter(requestFor("/actuator/health/readiness"))).isTrue();
    }

    @Test
    void filtersApplicationEndpoints() {
        assertThat(filter.shouldNotFilter(requestFor("/api/v1/alerts"))).isFalse();
    }

    private HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
