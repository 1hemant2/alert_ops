package com.alertops.flow_execution_engine.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.flow_execution_engine.dto.EscalationHistoryPageResponse;
import com.alertops.flow_execution_engine.service.EscalationHistoryService;

@RestController
@RequestMapping("/api/v1/escalation")
public class EscalationHistoryController {
    private final EscalationHistoryService historyService;

    // Creates the controller for read-only escalation activity history.
    public EscalationHistoryController(EscalationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{escalationId}/history")
    // Returns a paginated activity history for one team-owned escalation.
    public ResponseEntity<EscalationHistoryPageResponse> getHistory(
            @PathVariable UUID escalationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historyService.getHistory(escalationId, page, size));
    }
}
