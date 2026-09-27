package com.alertops.team.dto;

import java.time.Instant;

public record InvitePreviewDto(String email, String role, String teamName, Instant expiresAt) {
}
