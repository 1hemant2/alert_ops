package com.alertops.dto.schedular;


public class SheduleReq {
    String email;
    String message;
    Long delay;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public SheduleReq(String email, String message, Long delay) {
        this.email = email;
        this.message = message;
        this.delay = delay;
    }
}
