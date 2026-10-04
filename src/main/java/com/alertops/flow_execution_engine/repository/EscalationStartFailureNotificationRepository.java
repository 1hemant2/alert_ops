package com.alertops.flow_execution_engine.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow_execution_engine.model.EscalationStartFailureNotification;
import com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus;

@Repository
public interface EscalationStartFailureNotificationRepository
        extends JpaRepository<EscalationStartFailureNotification, UUID> {

    @Query("""
            SELECT n.id
            FROM EscalationStartFailureNotification n
            WHERE n.status IN :statuses
              AND n.nextAttemptAt <= :now
            ORDER BY n.nextAttemptAt ASC, n.id ASC
            """)
    List<UUID> findRecoverableIds(
            @Param("statuses") Collection<EscalationStartFailureNotificationStatus> statuses,
            @Param("now") Instant now);

    @Query("""
            SELECT n.id
            FROM EscalationStartFailureNotification n
            WHERE n.escalationId = :escalationId
              AND n.status IN :statuses
              AND n.nextAttemptAt <= :now
            ORDER BY n.id ASC
            """)
    List<UUID> findRecoverableIdsForEscalation(
            @Param("escalationId") UUID escalationId,
            @Param("statuses") Collection<EscalationStartFailureNotificationStatus> statuses,
            @Param("now") Instant now);

    @Query("""
            SELECT MIN(n.nextAttemptAt)
            FROM EscalationStartFailureNotification n
            WHERE n.status IN :statuses
            """)
    Instant findNextAttemptAt(
            @Param("statuses") Collection<EscalationStartFailureNotificationStatus> statuses);

    /**
     * A process can stop after claiming a row but before recording SENT. On a
     * new process, release those in-flight claims immediately so they are not
     * stranded behind the old process's lease.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE EscalationStartFailureNotification n
            SET n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.PENDING,
                n.nextAttemptAt = :now,
                n.updatedAt = :now,
                n.lastError = :reason
            WHERE n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENDING
            """)
    int requeueInFlightForRecovery(
            @Param("now") Instant now,
            @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query("""
            UPDATE EscalationStartFailureNotification n
            SET n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENDING,
                n.attemptCount = n.attemptCount + 1,
                n.lastAttemptAt = :now,
                n.nextAttemptAt = :leaseUntil,
                n.updatedAt = :now,
                n.lastError = null
            WHERE n.id = :id
              AND n.status IN (
                  com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.PENDING,
                  com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENDING)
              AND n.nextAttemptAt <= :now
            """)
    int claimForDelivery(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Transactional
    @Query("""
            UPDATE EscalationStartFailureNotification n
            SET n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENT,
                n.sentAt = :sentAt,
                n.nextAttemptAt = null,
                n.updatedAt = :sentAt,
                n.lastError = null
            WHERE n.id = :id
              AND n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENDING
            """)
    int markSent(@Param("id") UUID id, @Param("sentAt") Instant sentAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE EscalationStartFailureNotification n
            SET n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.PENDING,
                n.nextAttemptAt = :nextAttemptAt,
                n.updatedAt = :nextAttemptAt,
                n.lastError = :lastError
            WHERE n.id = :id
              AND n.status = com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus.SENDING
            """)
    int markPending(
            @Param("id") UUID id,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError);

    boolean existsByEscalationIdAndRecipientEmailIgnoreCase(UUID escalationId, String recipientEmail);
}
