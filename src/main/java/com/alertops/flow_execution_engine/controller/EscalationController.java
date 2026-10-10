package com.alertops.flow_execution_engine.controller;


import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.dto.CreateEscalationReqDto;
import com.alertops.flow_execution_engine.dto.ScheduledEscalationRequest;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.EscalationStartScheduler;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.flow_execution_engine.exception.EscalationException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/escalation")
public class EscalationController {
    private final EscalationService escalationService;
    private final FlowExecutionStateService flowExecutionStateService;
    private final StartFlowExecutionUseCase startFlowExecutionUseCase;
    private final EscalationStartScheduler escalationStartScheduler;

    EscalationController (EscalationService escalationService, FlowExecutionStateService flowExecutionStateService, 
            StartFlowExecutionUseCase startFlowExecutionUseCase,
            EscalationStartScheduler escalationStartScheduler) {
        this.escalationService = escalationService;
        this.flowExecutionStateService = flowExecutionStateService;
        this.startFlowExecutionUseCase = startFlowExecutionUseCase;
        this.escalationStartScheduler = escalationStartScheduler;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEsclation(@RequestBody CreateEscalationReqDto req) {
        if (req == null) {
            throw EscalationException.invalidRequest("Escalation details are required.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escalationService.createEscalation(
                        req.getEscalationName(), req.getTaskId(), req.getFlowId()));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getEscalation(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(escalationService.getEscalations(page, size, sortBy, sortDir));
    }

    @GetMapping
    public ResponseEntity<?> getEscalations(@RequestParam UUID escalationId) {
        var escalation = escalationService.getEscalationById(escalationId);
        return escalation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(escalation);
    }

    @GetMapping("/{escalationId}/execution-states")
    public ResponseEntity<?> getExecutionStates(@PathVariable UUID escalationId) {
        var executionStates = escalationService.getExecutionStates(escalationId);
        return executionStates == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(executionStates);
    }

    @PostMapping("/start")
    public ResponseEntity<?> startEscalation(@RequestBody Map<String, UUID> req) {
        if (req == null || req.get("escalationId") == null) {
            throw EscalationException.invalidRequest("An escalationId is required.");
        }
        UUID escalationId = req.get("escalationId");
        String result = startFlowExecutionUseCase.execute(flowExecutionStateService, escalationId);
        // The transaction has committed before the use case returns; now cancel only the old wake-up handle.
        escalationStartScheduler.cancel(escalationId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{escalationId}/start")
    public ResponseEntity<?> startEscalation(@PathVariable UUID escalationId) {
        String result = startFlowExecutionUseCase.execute(flowExecutionStateService, escalationId);
        // The transaction has committed before the use case returns; now cancel only the old wake-up handle.
        escalationStartScheduler.cancel(escalationId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{escalationId}/schedule")
    public ResponseEntity<?> schedule(
            @PathVariable UUID escalationId,
            @RequestBody ScheduledEscalationRequest request) {
        var escalation = escalationService.schedule(escalationId, request);
        return escalation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(escalation);
    }

    @PostMapping("/{escalationId}/reschedule")
    public ResponseEntity<?> reschedule(
            @PathVariable UUID escalationId,
            @RequestBody ScheduledEscalationRequest request) {
        var escalation = escalationService.reschedule(escalationId, request);
        return escalation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(escalation);
    }

    @PostMapping("/{escalationId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID escalationId) {
        var escalation = escalationService.cancelScheduled(escalationId);
        return escalation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(escalation);
    }

    // Stops future daily or weekly runs while keeping existing runs available.
    @PostMapping("/{escalationId}/stop-repeat")
    public ResponseEntity<?> stopRepeat(@PathVariable UUID escalationId) {
        var escalation = escalationService.stopRepeating(escalationId);
        return escalation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(escalation);
    }

}
