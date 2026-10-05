package com.alertops.flow_execution_engine.controller;

import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.EscalationStartScheduler;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalationControllerTest {
    @Test
    void createReturnsCreatedForPersistedEscalation() {
        EscalationService service = mock(EscalationService.class);
        Escalation escalation = new Escalation();
        var controller = new EscalationController(
                service,
                mock(FlowExecutionStateService.class),
                mock(StartFlowExecutionUseCase.class),
                mock(EscalationStartScheduler.class)
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

    @Test
    void startNowCancelsTheOldTimerAfterTheStartUseCaseReturns() {
        EscalationService service = mock(EscalationService.class);
        StartFlowExecutionUseCase startUseCase = mock(StartFlowExecutionUseCase.class);
        EscalationStartScheduler scheduler = mock(EscalationStartScheduler.class);
        var controller = new EscalationController(
                service,
                mock(FlowExecutionStateService.class),
                startUseCase,
                scheduler);
        UUID escalationId = UUID.randomUUID();
        when(startUseCase.execute(any(), org.mockito.ArgumentMatchers.eq(escalationId)))
                .thenReturn("started");

        var response = controller.startEscalation(escalationId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("started", response.getBody());
        verify(scheduler).cancel(escalationId);
    }
}
