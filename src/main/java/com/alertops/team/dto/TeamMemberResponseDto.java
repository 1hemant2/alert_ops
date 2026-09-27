package com.alertops.team.dto;

import java.util.UUID;

public record TeamMemberResponseDto(UUID memberId, UUID userId, String name, String email, String role) {
}
