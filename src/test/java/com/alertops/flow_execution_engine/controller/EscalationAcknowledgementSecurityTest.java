package com.alertops.flow_execution_engine.controller;

import com.alertops.auth.repository.UserRepository;
import com.alertops.flow_execution_engine.dto.EscalationAcknowledgementResponse;
import com.alertops.flow_execution_engine.service.EscalationAcknowledgementService;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EscalationAcknowledgementController.class)
@Import(SecurityConfig.class)
class EscalationAcknowledgementSecurityTest {
    @Autowired private MockMvc mockMvc;

    @MockitoBean private EscalationAcknowledgementService acknowledgementService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private TeamMemberRepository teamMemberRepository;
    @MockitoBean private UserRepository userRepository;

    @Test
    void anonymousEmailLinkCanPreviewAndConfirmUsingPost() throws Exception {
        when(acknowledgementService.preview("email-token"))
                .thenReturn(new EscalationAcknowledgementResponse(
                        "Database outage", "oncall@example.com", "OPEN", Instant.now().plusSeconds(600), null, null, false));
        when(acknowledgementService.acknowledge("email-token"))
                .thenReturn(new EscalationAcknowledgementResponse(
                        "Database outage", "oncall@example.com", "COMPLETED", Instant.now().plusSeconds(600), Instant.now(), "oncall@example.com", true));

        mockMvc.perform(post("/api/v1/escalation/acknowledgement/preview")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"email-token\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/escalation/acknowledgement/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"email-token\"}"))
                .andExpect(status().isOk());

        verify(acknowledgementService).preview("email-token");
        verify(acknowledgementService).acknowledge("email-token");
    }

    @Test
    void openingTheConfirmationEndpointWithGetCannotAcknowledge() throws Exception {
        mockMvc.perform(get("/api/v1/escalation/acknowledgement/confirm")
                        .param("token", "email-token"))
                .andExpect(status().isUnauthorized());

        verify(acknowledgementService, never()).acknowledge("email-token");
    }
}
