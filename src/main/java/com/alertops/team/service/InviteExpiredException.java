package com.alertops.team.service;

public class InviteExpiredException extends RuntimeException {
    public InviteExpiredException() {
        super("This invitation has expired.");
    }
}
