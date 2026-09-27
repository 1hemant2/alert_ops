package com.alertops.team.service;

public class InviteMailDeliveryException extends RuntimeException {
    public InviteMailDeliveryException() {
        super("The invitation email could not be sent. Check the SMTP configuration and try again.");
    }
}
