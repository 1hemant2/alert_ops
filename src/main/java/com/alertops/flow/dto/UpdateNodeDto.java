package com.alertops.flow.dto;

public class UpdateNodeDto {
    private String nodeName;
    private int durationInMinutes;
    private Integer resolutionTimeoutInMinutes;
    private String email;
    private Long version;

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public Integer getResolutionTimeoutInMinutes() {
        return resolutionTimeoutInMinutes;
    }

    public void setResolutionTimeoutInMinutes(Integer resolutionTimeoutInMinutes) {
        this.resolutionTimeoutInMinutes = resolutionTimeoutInMinutes;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
