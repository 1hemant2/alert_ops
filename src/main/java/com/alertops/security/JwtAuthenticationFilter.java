package com.alertops.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEALTH_ENDPOINT = "/actuator/health";
    private static final String TEAM_ENDPOINT = "/api/v1/team";
    private static final String TEAM_SELECT_ENDPOINT = "/api/v1/team/select";
    private static final String TEAM_JOIN_ENDPOINT = "/api/v1/team/join";

    private final JwtUtil jwtUtil;
    private final TeamMemberRepository teamMemberRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TeamMemberRepository teamMemberRepository) {
        this.jwtUtil = jwtUtil;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.equals(HEALTH_ENDPOINT)
                || requestUri.startsWith(HEALTH_ENDPOINT + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Claims claims;
                try {
                    claims = jwtUtil.parse(token);
                } catch (JwtException | IllegalArgumentException e) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                UUID userId = parseUuid(claims.getSubject());
                if (userId == null) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                UUID teamId = parseUuid(claims.get("teamId", String.class));
                if (requiresTeamMembership(request)) {
                    if (teamId == null) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Select a team before using this resource.");
                        return;
                    }

                    TeamMember membership = teamMemberRepository.findTeamMemeber(userId, teamId);
                    if (membership == null) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You are no longer a member of this team.");
                        return;
                    }

                    // Use the current role so role changes take effect without waiting for this token to expire.
                    AuthContextHolder.set(new AuthContext(
                            userId,
                            teamId,
                            membership.getRole(),
                            token,
                            claims.get("email", String.class)));
                } else {
                    AuthContextHolder.set(new AuthContext(
                            userId,
                            teamId,
                            claims.get("roles", String.class),
                            token,
                            claims.get("email", String.class)));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean requiresTeamMembership(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.equals(TEAM_SELECT_ENDPOINT) || path.equals(TEAM_JOIN_ENDPOINT)) {
            // These operations verify the target team using the requested team ID or invite token.
            return false;
        }

        return path.startsWith(TEAM_ENDPOINT + "/")
                || isPathOrSubpath(path, "/api/v1/task")
                || isPathOrSubpath(path, "/api/v1/flow")
                || isPathOrSubpath(path, "/api/v1/escalation");
    }

    private boolean isPathOrSubpath(String path, String basePath) {
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }
}
