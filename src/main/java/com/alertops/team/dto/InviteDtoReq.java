package com.alertops.team.dto;

public record InviteDtoReq(String email, String role, Long expiresInHours) {
}
