package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

class EscalationAcknowledgementServiceTest {
    private static final UUID ESCALATION_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final String RAW_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final EscalationAcknowledgementTokenRepository tokenRepository = org.mockito.Mockito.mock(EscalationAcknowledgementTokenRepository.class);
    private final EscalationRepository escalationRepository = org.mockito.Mockito.mock(EscalationRepository.class);
    private final EscalationAcknowledgementService service = new EscalationAcknowledgementService(
            tokenRepository, escalationRepository, Duration.ofHours(72), "https://alerts.example.com/");

    private Escalation escalation;
    private EscalationAcknowledgementToken token;

    @BeforeEach
    void setUp() {
        escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setName("Database outage");
        escalation.setStatus("OPEN");

        token = new EscalationAcknowledgementToken();
        token.setEscalationId(ESCALATION_ID);
        token.setRecipientEmail("oncall@example.com");
        token.setTokenHash("hash-is-looked-up-by-repository");
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void createsRecipientScopedExpiringLinkAndStoresOnlyItsHash() {
        String link = service.createAcknowledgementUrl(escalation, "oncall@example.com");

        assertTrue(link.startsWith("https://alerts.example.com/acknowledge?token="));
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        assertEquals(43, rawToken.length());
        org.mockito.ArgumentCaptor<EscalationAcknowledgementToken> savedToken =
                org.mockito.ArgumentCaptor.forClass(EscalationAcknowledgementToken.class);
        verify(tokenRepository).save(savedToken.capture());
        assertEquals(ESCALATION_ID, savedToken.getValue().getEscalationId());
        assertEquals("oncall@example.com", savedToken.getValue().getRecipientEmail());
        assertNotEquals(rawToken, savedToken.getValue().getTokenHash());
        assertEquals(64, savedToken.getValue().getTokenHash().length());
        assertTrue(savedToken.getValue().getExpiresAt().isAfter(savedToken.getValue().getCreatedAt()));
    }

    @Test
    void previewDoesNotChangeTheRunAndConfirmRecordsAcknowledgement() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var preview = service.preview(RAW_TOKEN);
        assertEquals("Database outage", preview.escalationName());
        assertEquals("oncall@example.com", preview.recipientEmail());
        assertFalse(preview.alreadyAcknowledged());
        verify(escalationRepository, never()).save(any(Escalation.class));

        var result = service.acknowledge(RAW_TOKEN);
        assertEquals("COMPLETED", escalation.getStatus());
        assertEquals("ACKNOWLEDGED", escalation.getResolutionType());
        assertEquals("oncall@example.com", escalation.getIssueSolvedBy());
        assertNotNull(escalation.getAcknowledgedAt());
        assertTrue(result.alreadyAcknowledged());
        verify(escalationRepository).findByIdForUpdate(ESCALATION_ID);
        verify(escalationRepository).save(escalation);
    }

    @Test
    void repeatedAcknowledgementReturnsTheSavedResult() {
        escalation.setStatus("COMPLETED");
        escalation.setResolutionType("ACKNOWLEDGED");
        escalation.setIssueSolvedBy("ONCALL@example.com ");
        escalation.setAcknowledgedAt(Instant.now().minusSeconds(30));
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var result = service.acknowledge(RAW_TOKEN);

        assertTrue(result.alreadyAcknowledged());
        assertEquals(escalation.getAcknowledgedAt(), result.acknowledgedAt());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void expiredLinkCannotAcknowledgeAnActiveRun() {
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.GONE, error.getStatusCode());
        assertEquals("OPEN", escalation.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void unknownTokenCannotAcknowledgeARun() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(escalationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void finishedRunCannotBeChangedByAnUnusedToken() {
        escalation.setStatus("COMPLETED");
        escalation.setResolutionType("EXHAUSTED");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void aTokenCannotBeUsedByAnotherRecipientAfterAcknowledgement() {
        escalation.setStatus("COMPLETED");
        escalation.setResolutionType("ACKNOWLEDGED");
        escalation.setIssueSolvedBy("another@example.com");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }
}
