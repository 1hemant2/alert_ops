package com.AlertOps.dto.Schedular;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


// public record ScheduledEvent(Long taskId, String email, String message) implements Serializable {}

@Getter
@Setter
@AllArgsConstructor
public class ScheduledEvent {
    private Long id;
    private String email;
    private String message;
}
