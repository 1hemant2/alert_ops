package com.AlertOps.dto.Schedular;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SheduleReq {
    String email;
    String message;
    Long delay;
}
