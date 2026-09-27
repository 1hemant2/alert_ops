package com.alertops.flow_execution_engine.controller;


import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.dto.CreateEscalationReqDto;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;

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

    EscalationController (EscalationService escalationService, FlowExecutionStateService flowExecutionStateService, 
            StartFlowExecutionUseCase startFlowExecutionUseCase) {
        this.escalationService = escalationService;
        this.flowExecutionStateService = flowExecutionStateService;
        this.startFlowExecutionUseCase = startFlowExecutionUseCase;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEsclation(@RequestBody CreateEscalationReqDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escalationService.createEscalation(req.getEscalationName(), req.getTaskId(), req.getFlowId()));
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
        UUID escalationId = req.get("escalationId");
        return ResponseEntity.ok(startFlowExecutionUseCase.execute(flowExecutionStateService, escalationId));
    }

}
