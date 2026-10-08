package com.alertops.webhook.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WebhookPayloadSizeFilter extends OncePerRequestFilter {
    private static final String WEBHOOK_EVENT_PATH = "/api/v1/webhooks/";
    private final long maxPayloadBytes;

    // Creates the request filter with the same size limit used by webhook persistence validation.
    public WebhookPayloadSizeFilter(
            @Value("${alertops.webhook.max-body-bytes:65536}") long maxPayloadBytes) {
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("The webhook payload limit must be positive");
        }
        this.maxPayloadBytes = maxPayloadBytes;
    }

    // Skips the size check for endpoints that do not accept webhook event bodies.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !requestPath.startsWith(WEBHOOK_EVENT_PATH)
                || !requestPath.endsWith("/events");
    }

    // Rejects oversized declared request bodies before Spring parses the JSON payload.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (contentLength(request) > maxPayloadBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The webhook payload is too large.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    // Reads the declared body size from the servlet API or its raw header fallback.
    private long contentLength(HttpServletRequest request) {
        try {
            String headerValue = request.getHeader("Content-Length");
            if (headerValue != null) {
                return Long.parseLong(headerValue);
            }
        } catch (NumberFormatException exception) {
            return request.getContentLengthLong();
        }
        return request.getContentLengthLong();
    }
}
