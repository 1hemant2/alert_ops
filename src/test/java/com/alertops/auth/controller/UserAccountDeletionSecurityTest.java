package com.alertops.auth.controller;

import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.auth.service.UserService;
import com.alertops.caching.IntentCache;
import com.alertops.security.JwtUtil;
import com.alertops.security.SecurityConfig;
import com.alertops.team.repository.TeamMemberRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserAccountDeletionSecurityTest {

    private static final String TOKEN = "valid-token";
    private static final UUID USER_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private IntentCache intentCache;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TeamMemberRepository teamMemberRepository;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parse(TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(USER_ID.toString());

        User verifiedUser = new User();
        verifiedUser.setEmailVerified(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(verifiedUser));
    }

    @Test
    void deniesAnonymousAccountDeletionWithoutCallingUserService() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/user")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"id": 123, "email": "victim@example.com"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void deniesAuthenticatedAccountDeletionWithoutCallingUserService() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/user")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"id": 123, "email": "victim@example.com"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }
}
