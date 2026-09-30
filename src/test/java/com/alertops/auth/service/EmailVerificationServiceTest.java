package com.alertops.auth.service;

import com.alertops.auth.model.EmailVerificationToken;
import com.alertops.auth.model.User;
import com.alertops.auth.repository.EmailVerificationTokenRepository;
import com.alertops.auth.repository.UserRepository;
import com.alertops.messaging.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {
    private static final UUID USER_ID = UUID.fromString("44000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailVerificationTokenRepository verificationTokenRepository = mock(EmailVerificationTokenRepository.class);
    private final EmailVerificationMailer verificationMailer = mock(EmailVerificationMailer.class);
    private final EmailVerificationService verificationService = new EmailVerificationService(
            userRepository,
            verificationTokenRepository,
            verificationMailer,
            "https://app.example.com",
            Duration.ofMinutes(30));

    @Test
    void issuesRandomSingleUseTokenAndSendsLink() {
        User account = mock(User.class);
        when(account.getId()).thenReturn(USER_ID);
        when(account.getEmail()).thenReturn("member@example.com");
        when(account.isEmailVerified()).thenReturn(false);

        verificationService.sendVerificationLink(account);

        ArgumentCaptor<EmailVerificationToken> verificationTokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(verificationTokenRepository).save(verificationTokenCaptor.capture());
        ArgumentCaptor<String> verificationUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(verificationMailer).sendVerificationEmail(eq(account), verificationUrlCaptor.capture());

        String verificationUrl = verificationUrlCaptor.getValue();
        String plainTextToken = verificationUrl.substring(verificationUrl.indexOf("token=") + "token=".length());
        assertThat(verificationUrl).startsWith("https://app.example.com/verify-email?token=");
        assertThat(plainTextToken).isNotBlank();
        assertThat(verificationTokenCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(verificationTokenCaptor.getValue().getTokenHash()).isEqualTo(sha256(plainTextToken));
        assertThat(verificationTokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void verifiesTokenAndConsumesIt() {
        String plainTextToken = "test-verification-token";
        EmailVerificationToken storedToken = new EmailVerificationToken();
        storedToken.setUserId(USER_ID);
        storedToken.setTokenHash(sha256(plainTextToken));
        storedToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        User account = new User();
        when(verificationTokenRepository.findAndLockByTokenHash(sha256(plainTextToken))).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(account));

        verificationService.verifyEmailAddress(plainTextToken);

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(storedToken.getConsumedAt()).isNotNull();
        verify(userRepository).save(account);
        verify(verificationTokenRepository).save(storedToken);
    }

    @Test
    void rejectsExpiredTokenWithoutChangingUser() {
        String plainTextToken = "expired-verification-token";
        EmailVerificationToken storedToken = new EmailVerificationToken();
        storedToken.setUserId(USER_ID);
        storedToken.setExpiresAt(Instant.now().minusSeconds(1));
        when(verificationTokenRepository.findAndLockByTokenHash(sha256(plainTextToken))).thenReturn(Optional.of(storedToken));

        assertThrows(EmailVerificationExpiredException.class, () -> verificationService.verifyEmailAddress(plainTextToken));
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
