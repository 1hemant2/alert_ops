package com.alertops.flow_execution_engine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.flow_execution_engine.dto.EscalationAcknowledgementResponse;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

@Service
public class EscalationAcknowledgementService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final EscalationAcknowledgementTokenRepository tokenRepository;
    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository flowExecutionStateRepository;
    private final Duration tokenLifetime;
    private final String uiBaseUrl;

    public EscalationAcknowledgementService(
            EscalationAcknowledgementTokenRepository tokenRepository,
            EscalationRepository escalationRepository,
            FlowExecutionStateRepository flowExecutionStateRepository,
            @Value("${alertops.escalation.acknowledgement-ttl:72h}") Duration tokenLifetime,
            @Value("${alertops.ui.base-url:http://localhost:5173}") String uiBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.escalationRepository = escalationRepository;
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.tokenLifetime = tokenLifetime;
        this.uiBaseUrl = uiBaseUrl == null ? "" : uiBaseUrl.replaceAll("/+$", "");
        if (tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("Acknowledgement link lifetime must be positive");
        }
    }

    @Transactional
    public String createAcknowledgementUrl(Escalation escalation, String recipientEmail) {
        if (escalation == null || escalation.getId() == null || isBlank(recipientEmail)) {
            throw new IllegalArgumentException("An escalation and recipient are required to create an acknowledgement link");
        }

        String rawToken = newRawToken();
        Instant now = Instant.now();
        EscalationAcknowledgementToken token = new EscalationAcknowledgementToken();
        token.setEscalationId(escalation.getId());
        token.setRecipientEmail(recipientEmail.trim());
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(tokenLifetime));
        tokenRepository.save(token);

        return uiBaseUrl + "/acknowledge?token=" + rawToken;
    }

    @Transactional(readOnly = true)
    public EscalationAcknowledgementResponse preview(String rawToken) {
        EscalationAcknowledgementToken token = findToken(rawToken);
        Escalation escalation = escalationRepository.findById(token.getEscalationId())
                .orElseThrow(() -> invalidToken());
        boolean alreadyAcknowledged = isAcknowledgedBy(escalation, token.getRecipientEmail());
        validateTokenAndRun(token, escalation, alreadyAcknowledged);
        return response(token, escalation, alreadyAcknowledged);
    }

    @Transactional
    public EscalationAcknowledgementResponse acknowledge(String rawToken) {
        EscalationAcknowledgementToken token = findToken(rawToken);
        Escalation escalation = escalationRepository.findByIdForUpdate(token.getEscalationId())
                .orElseThrow(() -> invalidToken());
        boolean alreadyAcknowledged = isAcknowledgedBy(escalation, token.getRecipientEmail());
        validateTokenAndRun(token, escalation, alreadyAcknowledged);

        if (!alreadyAcknowledged) {
            escalation.setStatus(EscalationStatus.COMPLETED);
            escalation.setResolutionType(EscalationResolutionType.ACKNOWLEDGED);
            escalation.setIssueSolvedBy(token.getRecipientEmail());
            escalation.setAcknowledgedAt(Instant.now());
            flowExecutionStateRepository.markUnsentStepsSkipped(escalation.getId());
            escalationRepository.save(escalation);
        }

        return response(token, escalation, true);
    }

    private EscalationAcknowledgementToken findToken(String rawToken) {
        if (isBlank(rawToken) || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalidToken();
        }
        return tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> invalidToken());
    }

    private void validateTokenAndRun(
            EscalationAcknowledgementToken token,
            Escalation escalation,
            boolean alreadyAcknowledged) {
        if (alreadyAcknowledged) {
            return;
        }
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This acknowledgement link has expired.");
        }
        if (escalation.getResolutionType() == EscalationResolutionType.ACKNOWLEDGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This escalation was acknowledged by another recipient.");
        }
        if (escalation.getStatus() != EscalationStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This escalation is no longer active.");
        }
    }

    private boolean isAcknowledgedBy(Escalation escalation, String recipientEmail) {
        return escalation.getResolutionType() == EscalationResolutionType.ACKNOWLEDGED
                && normalizeEmail(recipientEmail).equals(normalizeEmail(escalation.getIssueSolvedBy()));
    }

    private EscalationAcknowledgementResponse response(
            EscalationAcknowledgementToken token,
            Escalation escalation,
            boolean alreadyAcknowledged) {
        return new EscalationAcknowledgementResponse(
                escalation.getName(),
                token.getRecipientEmail(),
                escalation.getStatus() == null ? null : escalation.getStatus().name(),
                token.getExpiresAt(),
                escalation.getAcknowledgedAt(),
                escalation.getIssueSolvedBy(),
                alreadyAcknowledged);
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This acknowledgement link is invalid.");
    }
}
