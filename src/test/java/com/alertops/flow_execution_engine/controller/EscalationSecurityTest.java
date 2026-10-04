package com.alertops.flow_execution_engine.controller;

import com.alertops.auth.repository.UserRepository;
import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EscalationController.class)
@Import(SecurityConfig.class)
class EscalationSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EscalationService escalationService;

    @MockitoBean
    private FlowExecutionStateService flowExecutionStateService;

    @MockitoBean
    private StartFlowExecutionUseCase startFlowExecutionUseCase;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TeamMemberRepository teamMemberRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void anonymousEscalationRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/escalation")
                        .param("escalationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousScheduleRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/schedule", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRescheduleRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/reschedule", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCancelRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
