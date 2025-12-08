package com.alertops.dto.schedular;

import java.io.Serializable;



// public record ScheduledEvent(Long taskId, String email, String message) implements Serializable {}

public class ScheduledEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String email;
    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ScheduledEvent(Long id, String email, String message) {
        this.id = id;
        this.email = email;
        this.message = message;
    }
}
