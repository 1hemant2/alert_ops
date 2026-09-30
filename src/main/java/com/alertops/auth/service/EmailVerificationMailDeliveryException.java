package com.alertops.auth.service;

public class EmailVerificationMailDeliveryException extends RuntimeException {
    public EmailVerificationMailDeliveryException() {
        super("The verification email could not be sent. Check the SMTP configuration and try again.");
    }

    public EmailVerificationMailDeliveryException(Throwable cause) {
        super("The verification email could not be sent. Check the SMTP configuration and try again.", cause);
    }
}
