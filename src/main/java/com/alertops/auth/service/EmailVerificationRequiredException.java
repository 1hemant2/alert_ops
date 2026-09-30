package com.alertops.auth.service;

public class EmailVerificationRequiredException extends RuntimeException {
    public EmailVerificationRequiredException() {
        super("Verify your email address before signing in.");
    }
}
