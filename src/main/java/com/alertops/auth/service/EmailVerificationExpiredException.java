package com.alertops.auth.service;

public class EmailVerificationExpiredException extends RuntimeException {
    public EmailVerificationExpiredException() {
        super("This email verification link has expired.");
    }
}
