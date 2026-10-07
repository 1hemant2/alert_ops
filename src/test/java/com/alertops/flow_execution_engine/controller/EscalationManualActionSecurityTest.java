package com.alertops.flow_execution_engine.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.alertops.auth.repository.UserRepository;
import com.alertops.flow_execution_engine.dto.EscalationManualActionResponse;
import com.alertops.flow_execution_engine.service.EscalationManualActionService;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;

@WebMvcTest(EscalationManualActionController.class)
@Import(SecurityConfig.class)
class EscalationManualActionSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EscalationManualActionService manualActionService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TeamMemberRepository teamMemberRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    // Allows an anonymous recipient to preview a valid scoped action token.
    void anonymousRecipientCanPreviewAndConfirmEscalateNow() throws Exception {
        EscalationManualActionResponse response = new EscalationManualActionResponse(
                "Database outage", "OPEN", null, "first@example.com", null,
                "second@example.com", null, true, false, null);
        when(manualActionService.previewAsRecipient("email-token")).thenReturn(response);
        when(manualActionService.escalateNowAsRecipient("email-token")).thenReturn(response);

        mockMvc.perform(post("/api/v1/escalation/escalate-now/preview")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"email-token\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/escalation/escalate-now/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"email-token\"}"))
                .andExpect(status().isOk());

        verify(manualActionService).previewAsRecipient("email-token");
        verify(manualActionService).escalateNowAsRecipient("email-token");
    }

    @Test
    // Requires authentication for a signed-in team member's action endpoint.
    void anonymousTeamMemberActionIsRejected() throws Exception {
        UUID escalationId = UUID.randomUUID();
        String body = "{\"expectedSourceStepId\":\"73000000-0000-0000-0000-000000000004\","
                + "\"expectedTargetStepId\":\"73000000-0000-0000-0000-000000000005\"}";

        mockMvc.perform(post("/api/v1/escalation/{escalationId}/escalate-now", escalationId)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(manualActionService, never()).escalateNowAsTeamMember(org.mockito.ArgumentMatchers.eq(escalationId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    // Keeps the recipient confirmation operation POST-only.
    void getCannotConfirmEscalateNow() throws Exception {
        mockMvc.perform(get("/api/v1/escalation/escalate-now/confirm")
                        .param("token", "email-token"))
                .andExpect(status().isUnauthorized());

        verify(manualActionService, never()).escalateNowAsRecipient("email-token");
    }
}
