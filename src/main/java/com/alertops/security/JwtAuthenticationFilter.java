package com.alertops.security;


import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEALTH_ENDPOINT = "/actuator/health";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.parse(token);
            
            String teamIdStr = claims.get("teamId", String.class);
            String userIdStr = claims.getSubject();
            UUID teamId = null;
            UUID userId = null;

            if (teamIdStr != null) {
                try {
                    teamId = UUID.fromString(teamIdStr);
                } catch (IllegalArgumentException e) {
                    teamId = null;
                }
            }

            if(userIdStr != null) {
                try {
                   userId = UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    userId = null;
                }
            }

            String roles = claims.get("roles", String.class);
            String email = claims.get("email", String.class);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());

            SecurityContextHolder.getContext().setAuthentication(authentication);

            AuthContextHolder.set(
                    new AuthContext(userId, teamId, roles, token, email)
            );
        }

        try {
            filterChain.doFilter(request, response);
        } catch(Exception e) {
            System.out.println("Exception in filter chain: " + e.getMessage());
        } finally {
            AuthContextHolder.clear();
        }
    }
}
