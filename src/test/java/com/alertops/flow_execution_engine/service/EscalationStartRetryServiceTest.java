package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

class EscalationStartRetryServiceTest {
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final EscalationStartRetryService retryService = new EscalationStartRetryService(escalations, 3);
    private final UUID escalationId = UUID.randomUUID();
    private final Instant retryAt = Instant.parse("2026-01-01T00:00:05Z");

    @Test
    void recordsRetryCountAndNextRetryTime() {
        Escalation escalation = scheduledEscalation(0);
        when(escalations.findByIdForUpdate(escalationId)).thenReturn(Optional.of(escalation));
        when(escalations.save(any(Escalation.class))).thenReturn(escalation);

        Optional<Instant> result = retryService.recordFailureAndPlanRetry(escalationId, retryAt);

        assertEquals(Optional.of(retryAt), result);
        assertEquals(1, escalation.getScheduledStartRetryCount());
        assertEquals(retryAt, escalation.getScheduledStartNextRetryAt());
        verify(escalations).save(escalation);
    }

    @Test
    void marksEscalationFailedAfterRetryLimit() {
        Escalation escalation = scheduledEscalation(3);
        when(escalations.findByIdForUpdate(escalationId)).thenReturn(Optional.of(escalation));
        when(escalations.save(any(Escalation.class))).thenReturn(escalation);

        Optional<Instant> result = retryService.recordFailureAndPlanRetry(escalationId, retryAt);

        assertTrue(result.isEmpty());
        assertEquals(EscalationStatus.START_FAILED, escalation.getStatus());
        assertEquals(3, escalation.getScheduledStartRetryCount());
        assertNull(escalation.getScheduledStartNextRetryAt());
        verify(escalations).save(escalation);
    }

    @Test
    void doesNotWriteWhenEscalationIsMissingOrNoLongerScheduled() {
        when(escalations.findByIdForUpdate(escalationId)).thenReturn(Optional.empty());

        Optional<Instant> result = retryService.recordFailureAndPlanRetry(escalationId, retryAt);

        assertTrue(result.isEmpty());
        verify(escalations, never()).save(any(Escalation.class));
    }

    private Escalation scheduledEscalation(int retryCount) {
        Escalation escalation = new Escalation();
        escalation.setId(escalationId);
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartRetryCount(retryCount);
        return escalation;
    }
}
