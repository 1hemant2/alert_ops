package com.AlertOps.payload.request;

import lombok.Data;
import java.util.Set;

@Data
public class SignupRequest {
    private String username;
    private String email;
    private String password;
    private Set<String> role;
    private String name;
    private Long teamId;
    private String encryptedFilter;
}
