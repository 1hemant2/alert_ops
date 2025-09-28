package com.AlertOps.dto.Escalation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EscalationStepDto {
    private  Long userId;
    private  String userName;
    private String email;
}
