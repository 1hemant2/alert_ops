package com.alertops.dto.error;

import java.time.LocalDateTime;

public class ApiError {
    private String code;
    private String message;
    private String path;
    private LocalDateTime timestamp;

    public ApiError(String code, String message, String path) {
        this.code = code;
        this.message = message;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }

    // getters
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public LocalDateTime getTimestamp() { return timestamp; }
}

