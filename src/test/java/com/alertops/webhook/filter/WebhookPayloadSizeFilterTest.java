package com.alertops.webhook.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

class WebhookPayloadSizeFilterTest {
    // Rejects an oversized webhook request before the controller chain runs.
    @Test
    void rejectsOversizedDeclaredBody() throws Exception {
        WebhookPayloadSizeFilter filter = new WebhookPayloadSizeFilter(100);
        MockHttpServletRequest request = eventRequest();
        request.addHeader("Content-Length", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (request1, response1) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        assertThat(chainCalled).isFalse();
    }

    // Allows webhook requests at or below the configured size limit.
    @Test
    void allowsRequestWithinLimit() throws Exception {
        WebhookPayloadSizeFilter filter = new WebhookPayloadSizeFilter(100);
        MockHttpServletRequest request = eventRequest();
        request.addHeader("Content-Length", "100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (request1, response1) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chainCalled).isTrue();
    }

    // Leaves unrelated endpoints outside the webhook body-size guard.
    @Test
    void skipsNonWebhookEventRequests() throws Exception {
        WebhookPayloadSizeFilter filter = new WebhookPayloadSizeFilter(100);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/task");
        request.addHeader("Content-Length", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (request1, response1) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chainCalled).isTrue();
    }

    // Builds a request targeting the webhook event endpoint.
    private MockHttpServletRequest eventRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/webhooks/" + UUID.randomUUID() + "/events");
        return request;
    }
}
