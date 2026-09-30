package com.alertops.flow_execution_engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.flow_execution_engine.dto.EscalationAcknowledgementRequest;
import com.alertops.flow_execution_engine.service.EscalationAcknowledgementService;

@RestController
@RequestMapping("/api/v1/escalation/acknowledgement")
public class EscalationAcknowledgementController {
    private final EscalationAcknowledgementService acknowledgementService;

    public EscalationAcknowledgementController(EscalationAcknowledgementService acknowledgementService) {
        this.acknowledgementService = acknowledgementService;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody EscalationAcknowledgementRequest request) {
        return ResponseEntity.ok(acknowledgementService.preview(request.token()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> acknowledge(@RequestBody EscalationAcknowledgementRequest request) {
        return ResponseEntity.ok(acknowledgementService.acknowledge(request.token()));
    }
}
