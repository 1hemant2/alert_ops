package com.AlertOps.security.oauth2;

import com.AlertOps.model.User;
import com.AlertOps.repository.UserRepository;
import com.AlertOps.security.jwt.JwtUtils;
import com.AlertOps.security.services.UserDetailsImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.AlertOps.service.TeamService teamService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User user = userRepository.findByEmail(email);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setUserName(email); // Use email as username for OAuth
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Random password
            user.setEnabled(true); // OAuth2 users are verified by provider
            userRepository.save(user);
            
            // Create default team
            teamService.createDefaultTeamForUser(user);
        }

        // Create UserDetails for JWT generation
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        
        // We need to create a new Authentication object with UserDetails to generate the token correctly
        // Or modify JwtUtils to accept UserDetails directly. 
        // Let's assume JwtUtils takes Authentication. We can create a placeholder auth.
        // Actually, let's just use the userDetails to generate the token if JwtUtils supports it, 
        // or we can mock an Authentication object.
        
        // Ideally, we should update the SecurityContext, but for this handler, we just want the token.
        // Let's look at JwtUtils.generateJwtToken(Authentication authentication)
        // It uses authentication.getPrincipal().
        
        // So we can create a UsernamePasswordAuthenticationToken
        Authentication newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        
        String jwt = jwtUtils.generateJwtToken(newAuth);

        // Redirect to frontend with token
        // For now, redirect to a success page or print it.
        // Let's assume a frontend URL: http://localhost:3000/login/success?token=...
        // Or just return JSON? But this is a handler, so it expects a redirect or response write.
        
        response.setContentType("application/json");
        response.getWriter().write("{\"token\": \"" + jwt + "\"}");
        response.getWriter().flush();
        
        // If you want to redirect:
        // getRedirectStrategy().sendRedirect(request, response, "http://localhost:8080/api/auth/oauth2/success?token=" + jwt);
    }
}
