package com.alertops.team.dto;

import java.time.Instant;

public record InviteResponseDto(String email, String role, String teamName, Instant expiresAt) {
}
