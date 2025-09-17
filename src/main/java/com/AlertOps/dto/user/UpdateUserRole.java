package com.AlertOps.dto.user;

import lombok.Data;

@Data
public class UpdateUserRole {
    private  Long userId;
    private String existingRole;
    private String newRole;

}
