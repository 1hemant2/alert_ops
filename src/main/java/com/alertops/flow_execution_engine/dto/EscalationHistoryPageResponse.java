package com.alertops.flow_execution_engine.dto;

import java.util.List;

/** One stable page of saved escalation activity history. */
public record EscalationHistoryPageResponse(
        List<EscalationHistoryEventResponse> events,
        int page,
        int size,
        long totalEvents,
        int totalPages,
        boolean first,
        boolean last) {
}
