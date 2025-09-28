package com.AlertOps.controller;

import com.AlertOps.dto.Escalation.EscalationDto;
import com.AlertOps.repository.EscalationRepository;
import com.AlertOps.service.EscalationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/v1/escalation")
public class EscalationController {
    private final EscalationService escalationService;
    EscalationController (EscalationService escalationService) {
        this.escalationService = escalationService;
    }
    @PostMapping("")
    public ResponseEntity<?> createEsclation(@RequestBody EscalationDto req) {
         Long userId = req.getOwnerId();
         boolean isFlowStart = req.isFlowStarted();
         String startTime = req.getStartTime();
         String flowName = req.getFlowName();
         List<Long> taskAssigneeIds = req.getSteps();
         int created =  escalationService.createEscalation(flowName, userId, taskAssigneeIds, isFlowStart, startTime);
        if(created == 1) {
           return ResponseEntity.ok("task created successfullly");
        }
        return ResponseEntity.status(400).body("something went wrong");
    }

    @GetMapping("")
    public ResponseEntity<?> getEscalationSteps(@RequestBody EscalationDto req) {
        Long userId = req.getOwnerId();
        boolean isFlowStart = req.isFlowStarted();
        String startTime = req.getStartTime();
        String flowName = req.getFlowName();
        List<Long> taskAssigneeIds = req.getSteps();
        int created =  escalationService.createEscalation(flowName, userId, taskAssigneeIds, isFlowStart, startTime);
        if(created == 1) {
            return ResponseEntity.ok("task created successfullly");
        }
        return ResponseEntity.status(400).body("something went wrong");
    }

}
