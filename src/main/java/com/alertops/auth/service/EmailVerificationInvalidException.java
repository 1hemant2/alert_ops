package com.alertops.auth.service;

public class EmailVerificationInvalidException extends RuntimeException {
    public EmailVerificationInvalidException() {
        super("This email verification link is invalid or has already been used.");
    }
}
