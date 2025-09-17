package com.AlertOps.dto.user;

import lombok.Data;

@Data
public class UpdateUserRole {
    private  String userId;
    private String updatedRole;
    private String action;
}
