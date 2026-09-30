package com.alertops.auth.service;

import com.alertops.auth.dto.UserLoginDto;
import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final UserService userService = new UserService(
            userRepository, passwordEncoder, authenticationManager, jwtUtil, emailVerificationService);

    @Test
    void doesNotIssueJwtForAnUnverifiedAccount() {
        User account = mock(User.class);
        when(account.isEmailVerified()).thenReturn(false);
        when(userRepository.findByEmail("member@example.com")).thenReturn(account);

        UserLoginDto loginRequest = new UserLoginDto();
        loginRequest.setEmail("member@example.com");
        loginRequest.setPassword("password");

        assertThrows(EmailVerificationRequiredException.class, () -> userService.login(loginRequest));
        verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.any());
        verify(jwtUtil, never()).generateToken(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
