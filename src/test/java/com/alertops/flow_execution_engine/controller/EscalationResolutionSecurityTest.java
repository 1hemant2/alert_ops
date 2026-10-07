package com.alertops.flow_execution_engine.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.alertops.auth.repository.UserRepository;
import com.alertops.flow_execution_engine.dto.EscalationResolutionResponse;
import com.alertops.flow_execution_engine.service.EscalationResolutionService;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;

@WebMvcTest(EscalationResolutionController.class)
@Import(SecurityConfig.class)
class EscalationResolutionSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EscalationResolutionService resolutionService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TeamMemberRepository teamMemberRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void anonymousRecipientCanResolveWithTheExplicitTokenPost() throws Exception {
        when(resolutionService.resolveAsRecipient("email-token"))
                .thenReturn(new EscalationResolutionResponse(
                        "Database outage", "RESOLVED", "oncall@example.com", Instant.now(), false));

        mockMvc.perform(post("/api/v1/escalation/resolution/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"email-token\"}"))
                .andExpect(status().isOk());

        verify(resolutionService).resolveAsRecipient("email-token");
    }

    @Test
    void anonymousTeamResolveCannotUseTheAuthenticatedRoute() throws Exception {
        UUID escalationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/escalation/{escalationId}/resolve", escalationId))
                .andExpect(status().isUnauthorized());

        verify(resolutionService, never()).resolveAsTeamMember(escalationId);
    }

    @Test
    void getCannotResolveByRecipientToken() throws Exception {
        mockMvc.perform(get("/api/v1/escalation/resolution/confirm")
                        .param("token", "email-token"))
                .andExpect(status().isUnauthorized());

        verify(resolutionService, never()).resolveAsRecipient("email-token");
    }
}
