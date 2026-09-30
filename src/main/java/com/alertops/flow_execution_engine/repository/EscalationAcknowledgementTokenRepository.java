package com.alertops.flow_execution_engine.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;

public interface EscalationAcknowledgementTokenRepository
        extends JpaRepository<EscalationAcknowledgementToken, UUID> {
    Optional<EscalationAcknowledgementToken> findByTokenHash(String tokenHash);
}
