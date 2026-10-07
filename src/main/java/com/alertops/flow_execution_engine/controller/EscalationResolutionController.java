package com.alertops.flow_execution_engine.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.flow_execution_engine.dto.EscalationResolutionRequest;
import com.alertops.flow_execution_engine.service.EscalationResolutionService;

@RestController
@RequestMapping("/api/v1/escalation")
public class EscalationResolutionController {
    private final EscalationResolutionService resolutionService;

    public EscalationResolutionController(EscalationResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @PostMapping("/{escalationId}/resolve")
    public ResponseEntity<?> resolveAsTeamMember(@PathVariable UUID escalationId) {
        return ResponseEntity.ok(resolutionService.resolveAsTeamMember(escalationId));
    }

    @PostMapping("/resolution/confirm")
    public ResponseEntity<?> resolveAsRecipient(@RequestBody EscalationResolutionRequest request) {
        return ResponseEntity.ok(resolutionService.resolveAsRecipient(request == null ? null : request.token()));
    }
}
