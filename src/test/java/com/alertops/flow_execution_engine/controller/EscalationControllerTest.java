package com.alertops.flow_execution_engine.controller;

import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscalationControllerTest {
    @Test
    void createReturnsCreatedForPersistedEscalation() {
        EscalationService service = mock(EscalationService.class);
        Escalation escalation = new Escalation();
        var controller = new EscalationController(
                service,
                mock(FlowExecutionStateService.class),
                mock(StartFlowExecutionUseCase.class)
        );
        var request = new com.alertops.flow_execution_engine.dto.CreateEscalationReqDto();
        request.setEscalationName("API latency");
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        request.setTaskId(taskId);
        request.setFlowId(flowId);
        when(service.createEscalation("API latency", taskId, flowId)).thenReturn(escalation);

        var response = controller.createEsclation(request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(escalation, response.getBody());
    }
}
