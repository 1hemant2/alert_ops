package com.alertops.flow_execution_engine.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Local schedule fields supplied by the UI; the service converts them to UTC. */
public class ScheduledEscalationRequest {
    private LocalDate scheduleDate;
    private LocalTime scheduleTime;
    private String timezone;

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public LocalTime getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(LocalTime scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
