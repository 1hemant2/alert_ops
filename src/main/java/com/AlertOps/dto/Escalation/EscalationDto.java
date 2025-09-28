package com.AlertOps.dto.Escalation;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EscalationDto {
    private Long id;
    private String flowName;
    private String startFlow;
    private String startTime;
    // If you want owner info, flatten it:
    private Long ownerId;
    private String ownerName;
    private boolean isFlowStarted;
    List<Long> steps;
}
