package com.alertops.auth.dto;

import com.alertops.auth.model.User;

import java.util.Date;
import java.util.UUID;

public record UserResponseDto(UUID id, String name, String email, Date createdAt, boolean emailVerified) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(), user.getName(), user.getEmail(), user.getCreatedAt(), user.isEmailVerified());
    }
}
