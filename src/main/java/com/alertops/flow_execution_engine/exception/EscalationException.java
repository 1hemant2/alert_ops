package com.alertops.flow_execution_engine.exception;

import com.alertops.exception.AppException;
import org.springframework.http.HttpStatus;

public class EscalationException extends AppException {
    private EscalationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static EscalationException startConflict() {
        return new EscalationException(
                "ESCALATION_START_CONFLICT",
                "This escalation has already started or is no longer available to start.",
                HttpStatus.CONFLICT
        );
    }

    public static EscalationException invalidRequest(String message) {
        return new EscalationException("ESCALATION_INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    public static EscalationException unauthorized() {
        return new EscalationException(
                "ESCALATION_UNAUTHORIZED",
                "Authentication is required for this escalation action.",
                HttpStatus.UNAUTHORIZED
        );
    }

    public static EscalationException forbidden(String message) {
        return new EscalationException("ESCALATION_FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static EscalationException notFound() {
        return new EscalationException(
                "ESCALATION_NOT_FOUND",
                "The escalation is not available.",
                HttpStatus.NOT_FOUND
        );
    }

    public static EscalationException transitionConflict(String message) {
        return new EscalationException("ESCALATION_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
