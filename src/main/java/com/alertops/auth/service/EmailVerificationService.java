package com.alertops.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.auth.model.EmailVerificationToken;
import com.alertops.auth.model.User;
import com.alertops.auth.repository.EmailVerificationTokenRepository;
import com.alertops.auth.repository.UserRepository;
import com.alertops.messaging.EmailVerificationMailer;

@Service
public class EmailVerificationService {
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final EmailVerificationMailer verificationMailer;
    private final String verificationUiBaseUrl;
    private final Duration verificationTokenLifetime;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository verificationTokenRepository,
            EmailVerificationMailer verificationMailer,
            @Value("${alertops.ui.base-url}") String verificationUiBaseUrl,
            @Value("${alertops.auth.email-verification-ttl:30m}") Duration verificationTokenLifetime) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.verificationMailer = verificationMailer;
        this.verificationUiBaseUrl = verificationUiBaseUrl == null ? "" : verificationUiBaseUrl.replaceAll("/+$", "");
        if (verificationTokenLifetime.isNegative() || verificationTokenLifetime.isZero()) {
            throw new IllegalArgumentException("Email verification token lifetime must be positive");
        }
        this.verificationTokenLifetime = verificationTokenLifetime;
    }

    @Transactional
    public void sendVerificationLink(User user) {
        if (user == null || user.getId() == null || user.isEmailVerified()) {
            return;
        }
        if (verificationUiBaseUrl.isBlank()) {
            throw new IllegalStateException("The email verification URL is not configured.");
        }

        Instant issuedAt = Instant.now();
        verificationTokenRepository.invalidateUnusedTokensForUser(user.getId(), issuedAt);

        String verificationTokenValue = createVerificationToken();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(user.getId());
        verificationToken.setTokenHash(hashToken(verificationTokenValue));
        verificationToken.setExpiresAt(issuedAt.plus(verificationTokenLifetime));
        verificationTokenRepository.save(verificationToken);

        String verificationUrl = verificationUiBaseUrl + "/verify-email?token=" + verificationTokenValue;
        verificationMailer.sendVerificationEmail(user, verificationUrl);
    }

    @Transactional
    public void verifyEmailAddress(String verificationTokenValue) {
        if (verificationTokenValue == null || verificationTokenValue.isBlank()) {
            throw new EmailVerificationInvalidException();
        }

        EmailVerificationToken verificationToken = verificationTokenRepository
                .findAndLockByTokenHash(hashToken(verificationTokenValue.trim()))
                .orElseThrow(EmailVerificationInvalidException::new);
        if (verificationToken.getConsumedAt() != null) {
            throw new EmailVerificationInvalidException();
        }
        if (Instant.now().isAfter(verificationToken.getExpiresAt())) {
            throw new EmailVerificationExpiredException();
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(EmailVerificationInvalidException::new);
        user.setEmailVerified(true);
        userRepository.save(user);
        verificationToken.setConsumedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);
    }

    @Transactional
    public void resendVerificationLink(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return;
        }
        User user = userRepository.findByEmailIgnoreCase(emailAddress.trim());
        if (user != null && !user.isEmailVerified()) {
            sendVerificationLink(user);
        }
    }

    private String createVerificationToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String tokenValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
