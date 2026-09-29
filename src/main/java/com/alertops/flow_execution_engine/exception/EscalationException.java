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
}
