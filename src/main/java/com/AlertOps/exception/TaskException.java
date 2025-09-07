package com.AlertOps.exception;

import org.springframework.http.HttpStatus;

public class TaskException extends AppException {

    public TaskException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public TaskException(String code, String message, HttpStatus status, Throwable cause) {
        super(code, message, status, cause);
    }

    // Static factory helpers
    public static TaskException creationFailed(Throwable cause) {
        return new TaskException("TASK_CREATION_FAILED", "Failed to create task", HttpStatus.BAD_REQUEST, cause);
    }

    public static TaskException notFound(Long taskId) {
        return new TaskException("TASK_NOT_FOUND", "Task with id " + taskId + " not found", HttpStatus.NOT_FOUND);
    }

    public static TaskException getFailed(Throwable cause) {
        return new TaskException("TASK_GET_FAILED", "Failed to get task", HttpStatus.BAD_REQUEST, cause);
    }
}
