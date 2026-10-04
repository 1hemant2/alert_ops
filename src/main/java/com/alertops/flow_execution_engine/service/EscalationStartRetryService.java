package com.alertops.flow_execution_engine.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

/** Persists the retry policy for scheduled escalation starts. */
@Service
public class EscalationStartRetryService {
    private final EscalationRepository escalationRepository;
    private final int maxRetries;

    public EscalationStartRetryService(
            EscalationRepository escalationRepository,
            @Value("${alertops.scheduler.start-max-retries:3}") int maxRetries) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Scheduled-start max retries cannot be negative");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Records one failed start and returns the next retry time when another retry is allowed.
     * The row lock keeps the retry count correct when more than one callback sees the same
     * scheduled escalation.
     */
    @Transactional
    public Optional<Instant> recordFailureAndPlanRetry(UUID escalationId, Instant retryAt) {
        if (escalationId == null || retryAt == null) {
            return Optional.empty();
        }

        Escalation escalation = escalationRepository.findByIdForUpdate(escalationId).orElse(null);
        if (escalation == null || !"SCHEDULED".equals(escalation.getStatus())) {
            return Optional.empty();
        }

        int retriesUsed = escalation.getScheduledStartRetryCount();
        if (retriesUsed >= maxRetries) {
            escalation.setStatus("START_FAILED");
            escalation.setScheduledStartNextRetryAt(null);
            escalationRepository.save(escalation);
            return Optional.empty();
        }

        escalation.setScheduledStartRetryCount(retriesUsed + 1);
        escalation.setScheduledStartNextRetryAt(retryAt);
        escalationRepository.save(escalation);
        return Optional.of(retryAt);
    }
}
