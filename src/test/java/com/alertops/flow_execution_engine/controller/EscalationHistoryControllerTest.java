package com.alertops.flow_execution_engine.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alertops.flow_execution_engine.dto.EscalationHistoryPageResponse;
import com.alertops.flow_execution_engine.service.EscalationHistoryService;

class EscalationHistoryControllerTest {
    @Test
    // Verifies that the history endpoint returns the service's paginated response.
    void returnsPaginatedHistory() {
        EscalationHistoryService service = mock(EscalationHistoryService.class);
        EscalationHistoryController controller = new EscalationHistoryController(service);
        UUID escalationId = UUID.randomUUID();
        EscalationHistoryPageResponse expected = new EscalationHistoryPageResponse(
                List.of(), 0, 20, 0, 0, true, true);
        when(service.getHistory(escalationId, 0, 20)).thenReturn(expected);

        var response = controller.getHistory(escalationId, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
    }
}
