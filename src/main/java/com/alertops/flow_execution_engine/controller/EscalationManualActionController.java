package com.alertops.flow_execution_engine.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.flow_execution_engine.dto.EscalationManualActionRequest;
import com.alertops.flow_execution_engine.dto.EscalationManualActionTokenRequest;
import com.alertops.flow_execution_engine.service.EscalationManualActionService;

@RestController
@RequestMapping("/api/v1/escalation")
public class EscalationManualActionController {
    private final EscalationManualActionService manualActionService;

    // Creates the controller for authenticated and recipient Escalate now actions.
    public EscalationManualActionController(EscalationManualActionService manualActionService) {
        this.manualActionService = manualActionService;
    }

    // Previews the expected next step for an authenticated team member.
    @PostMapping("/{escalationId}/escalate-now/preview")
    public ResponseEntity<?> previewAsTeamMember(
            @PathVariable UUID escalationId,
            @RequestBody EscalationManualActionRequest request) {
        return ResponseEntity.ok(manualActionService.previewAsTeamMember(escalationId, request));
    }

    // Applies the expected Escalate now action for an authenticated team member.
    @PostMapping("/{escalationId}/escalate-now")
    public ResponseEntity<?> escalateNowAsTeamMember(
            @PathVariable UUID escalationId,
            @RequestBody EscalationManualActionRequest request) {
        return ResponseEntity.ok(manualActionService.escalateNowAsTeamMember(escalationId, request));
    }

    // Previews a recipient-scoped Escalate now link without changing state.
    @PostMapping("/escalate-now/preview")
    public ResponseEntity<?> previewAsRecipient(@RequestBody EscalationManualActionTokenRequest request) {
        return ResponseEntity.ok(manualActionService.previewAsRecipient(request == null ? null : request.token()));
    }

    // Applies a recipient-scoped Escalate now link after rechecking current state.
    @PostMapping("/escalate-now/confirm")
    public ResponseEntity<?> escalateNowAsRecipient(@RequestBody EscalationManualActionTokenRequest request) {
        return ResponseEntity.ok(manualActionService.escalateNowAsRecipient(request == null ? null : request.token()));
    }
}
