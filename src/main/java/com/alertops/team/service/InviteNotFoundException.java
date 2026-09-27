package com.alertops.team.service;

public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException() {
        super("This invitation could not be found.");
    }
}
