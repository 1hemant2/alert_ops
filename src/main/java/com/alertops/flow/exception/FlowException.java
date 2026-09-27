package com.alertops.flow.exception;

import org.springframework.http.HttpStatus;

import com.alertops.exception.AppException;

public class FlowException extends AppException {
    private FlowException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static FlowException invalid(String message) {
        return new FlowException("INVALID_FLOW_STEP", message, HttpStatus.BAD_REQUEST);
    }

    public static FlowException stepNotFound() {
        return new FlowException("FLOW_STEP_NOT_FOUND", "That step could not be found in this team.", HttpStatus.NOT_FOUND);
    }

    public static FlowException staleVersion() {
        return new FlowException("FLOW_VERSION_CONFLICT", "This path changed since it was loaded. Refresh and try again.", HttpStatus.CONFLICT);
    }
}
