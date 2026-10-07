package com.alertops.flow_execution_engine.controller;

import com.alertops.auth.repository.UserRepository;
import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.EscalationStartScheduler;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
    private EscalationStartScheduler escalationStartScheduler;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TeamMemberRepository teamMemberRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    // Requires authentication before creating an escalation.
    void anonymousCreateRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/create")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before reading the team escalation list.
    void anonymousListRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/escalation/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before starting an escalation by path.
    void anonymousPathStartRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/start", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before starting an escalation by request body.
    void anonymousBodyStartRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/start")
                        .contentType(APPLICATION_JSON)
                        .content("{\"escalationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before reading saved execution steps.
    void anonymousExecutionStateRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/escalation/{escalationId}/execution-states", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before reading one escalation.
    void anonymousEscalationRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/escalation")
                        .param("escalationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before scheduling a run.
    void anonymousScheduleRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/schedule", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before rescheduling a run.
    void anonymousRescheduleRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/reschedule", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    // Requires authentication before cancelling a scheduled run.
    void anonymousCancelRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/escalation/{escalationId}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

}
