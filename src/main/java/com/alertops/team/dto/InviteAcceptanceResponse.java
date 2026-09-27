package com.alertops.team.dto;

import java.util.UUID;

public record InviteAcceptanceResponse(UUID teamId, String teamName, String role) {
}
