package com.alertops.flow.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.flow.application.CreateFlowNodeUseCase;
import com.alertops.flow.dto.CreateNodeDto;
import com.alertops.flow.dto.ReorderNodeDto;
import com.alertops.flow.service.FlowService;

@RestController
@RequestMapping("/api/v1/flow")
public class FlowController {
    private final CreateFlowNodeUseCase createFlowNodeUseCase;
    private final FlowService flowService;

    public FlowController(FlowService flowService, CreateFlowNodeUseCase createFlowNodeUseCase) {
        this.createFlowNodeUseCase = createFlowNodeUseCase;
        this.flowService = flowService;
    }
    
    @PostMapping 
    public ResponseEntity<?> createFlow(@RequestBody Map<String, String> body) {
        String flowName = body.get("flowName");
        return ResponseEntity.ok(flowService.createFlow(flowName));
    }

    @GetMapping
    public ResponseEntity<?> getFlowById(@RequestParam String flowId) {
        return ResponseEntity.ok(flowService.getFlowById(java.util.UUID.fromString(flowId)));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllFlows(
        @RequestParam(defaultValue = "0") int page, 
        @RequestParam(defaultValue = "10") int size, 
        @RequestParam(defaultValue = "id") String sortBy, 
        @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(flowService.getFlowsByTeamId(page, size, sortBy, sortDir));
    }

    @PostMapping("/node")
    public ResponseEntity<?> createNode(@RequestBody CreateNodeDto nodeData) {
        return ResponseEntity.ok(createFlowNodeUseCase.execute(nodeData, flowService));
    }

    @GetMapping("/node/all")
    public ResponseEntity<?> getNodesByFlowId(@RequestParam String flowId) {
        return ResponseEntity.ok(flowService.getNodesByFlowId(java.util.UUID.fromString(flowId)));
    }

    @PatchMapping("/node/reorder")
    public ResponseEntity<?> reorderNode(@RequestBody ReorderNodeDto reorderNodeDto) {
        return ResponseEntity.ok(flowService.reorderNode(java.util.UUID.fromString(reorderNodeDto.getNodeId()), reorderNodeDto.getAfterNodeId(), reorderNodeDto.getVersion()));
    }
}
