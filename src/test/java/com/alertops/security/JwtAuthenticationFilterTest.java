package com.alertops.security;

import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid-token";
    private static final UUID USER_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final Claims claims = mock(Claims.class);
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, teamMemberRepository);
        when(jwtUtil.parse(TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(USER_ID.toString());
        when(claims.get("teamId", String.class)).thenReturn(TEAM_ID.toString());
        when(claims.get("roles", String.class)).thenReturn("USER");
        when(claims.get("email", String.class)).thenReturn("member@example.com");
    }

    @Test
    void skipsMainHealthEndpoint() {
        assertThat(filter.shouldNotFilter(requestFor("/actuator/health"))).isTrue();
    }

    @Test
    void skipsHealthEndpointSubpaths() {
        assertThat(filter.shouldNotFilter(requestFor("/actuator/health/readiness"))).isTrue();
    }

    @Test
    void filtersApplicationEndpoints() {
        assertThat(filter.shouldNotFilter(requestFor("/api/v1/alerts"))).isFalse();
    }

    @Test
    void deniesTeamScopedRequestWhenTheTokenHasNoSelectedTeam() throws Exception {
        when(claims.get("teamId", String.class)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestFor("/api/v1/task"), response, (request, response1) -> { });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    void deniesTeamScopedRequestWhenMembershipHasBeenRevoked() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestFor("/api/v1/task"), response, (request, response1) -> {
            throw new AssertionError("Revoked member request must not reach the controller");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(teamMemberRepository).findTeamMemeber(USER_ID, TEAM_ID);
    }

    @Test
    void usesCurrentDatabaseRoleForSelectedTeamRequests() throws Exception {
        TeamMember membership = new TeamMember();
        membership.setRole("ADMIN");
        when(teamMemberRepository.findTeamMemeber(USER_ID, TEAM_ID)).thenReturn(membership);
        AtomicReference<String> roleSeenByController = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestFor("/api/v1/flow"), response,
                (request, response1) -> roleSeenByController.set(AuthContextHolder.get().getRole()));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(roleSeenByController.get()).isEqualTo("ADMIN");
        assertThat(AuthContextHolder.get()).isNull();
    }

    @Test
    void allowsTeamSelectionToValidateTheRequestedTeamInsteadOfOldMembership() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestFor("/api/v1/team/select"), response, (request, response1) -> { });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(teamMemberRepository, never()).findTeamMemeber(USER_ID, TEAM_ID);
    }

    private HttpServletRequest requestFor(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setServletPath(uri);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
