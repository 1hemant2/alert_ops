package com.AlertOps.dto.Escalation;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
public class EscalationResDto {
    private Long flowId;
    private  String flowName;
    private Boolean isFlowStarted;
    private ZonedDateTime startTime; // preserves full ZoneId
    private ZonedDateTime createdAt;
}
