package com.alertops.webhook.exception;

import org.springframework.http.HttpStatus;

import com.alertops.exception.AppException;

public class WebhookException extends AppException {
    private WebhookException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static WebhookException badRequest(String message) {
        return new WebhookException("WEBHOOK_INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    public static WebhookException unauthorized() {
        return new WebhookException("WEBHOOK_UNAUTHORIZED", "The webhook secret is invalid.", HttpStatus.UNAUTHORIZED);
    }

    public static WebhookException forbidden(String message) {
        return new WebhookException("WEBHOOK_FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static WebhookException notFound() {
        return new WebhookException("WEBHOOK_NOT_FOUND", "The webhook is not available.", HttpStatus.NOT_FOUND);
    }

    public static WebhookException conflict(String message) {
        return new WebhookException("WEBHOOK_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public static WebhookException tooLarge() {
        return new WebhookException("WEBHOOK_PAYLOAD_TOO_LARGE", "The webhook payload is too large.", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    public static WebhookException rateLimited() {
        return new WebhookException("WEBHOOK_RATE_LIMITED", "Too many webhook requests. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
