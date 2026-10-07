package com.alertops.flow_execution_engine.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, UUID> {
      Page<Escalation> findByTeamId(UUID teamId, Pageable pageable);

      @Query("select e from Escalation e where e.id = :id and e.teamId = :teamId")
      Escalation findByIdAndTeamId(UUID id, UUID teamId);

      @Lock(LockModeType.PESSIMISTIC_WRITE)
      @Query("select e from Escalation e where e.id = :id")
      Optional<Escalation> findByIdForUpdate(@Param("id") UUID id);

      List<Escalation> findAllByStatus(EscalationStatus status);

      @Query("""
              select e from Escalation e
              where e.status = com.alertops.flow_execution_engine.model.EscalationStatus.ACKNOWLEDGED
                and e.acknowledgedStepId is not null
                and e.resolutionDeadline is not null
              order by e.resolutionDeadline asc, e.id asc
              """)
      // Finds acknowledged escalations that still have a durable resolution timeout.
      List<Escalation> findPendingResolutionTimeouts(Pageable pageable);

      @Query("""
              select e from Escalation e
              where e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED
                and e.scheduledStartAt is not null
              order by e.scheduledStartAt asc, e.id asc
              """)
      List<Escalation> findAllScheduled();

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.status = com.alertops.flow_execution_engine.model.EscalationStatus.OPEN,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id
                AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.IDLE
              """)
      int claimIdleForStart(@Param("id") UUID id, @Param("teamId") UUID teamId);

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.status = com.alertops.flow_execution_engine.model.EscalationStatus.OPEN,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id
                AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED
                AND e.scheduledStartAt <= :now
              """)
      int claimScheduledForStart(
              @Param("id") UUID id,
              @Param("teamId") UUID teamId,
              @Param("now") Instant now);

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.status = com.alertops.flow_execution_engine.model.EscalationStatus.OPEN,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id
                AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED
              """)
      int claimScheduledForManualStart(
              @Param("id") UUID id,
              @Param("teamId") UUID teamId);

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED,
                  e.scheduledStartAt = :scheduledStartAt,
                  e.scheduleTimezone = :scheduleTimezone,
                  e.scheduledByUserId = :scheduledByUserId,
                  e.scheduledByUserEmail = :scheduledByUserEmail,
                  e.scheduledStartRetryCount = 0,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.IDLE
              """)
      int scheduleIdle(
              @Param("id") UUID id,
              @Param("teamId") UUID teamId,
              @Param("scheduledStartAt") Instant scheduledStartAt,
              @Param("scheduleTimezone") String scheduleTimezone,
              @Param("scheduledByUserId") UUID scheduledByUserId,
              @Param("scheduledByUserEmail") String scheduledByUserEmail);

      @Modifying(clearAutomatically = true)
      @Query("""
              UPDATE Escalation e
              SET e.status = com.alertops.flow_execution_engine.model.EscalationStatus.CANCELLED,
                  e.cancelledAt = :now,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED
              """)
      int cancelScheduled(
              @Param("id") UUID id,
              @Param("teamId") UUID teamId,
              @Param("now") Instant now);

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.scheduledStartAt = :scheduledStartAt,
                  e.scheduleTimezone = :scheduleTimezone,
                  e.scheduledByUserId = :scheduledByUserId,
                  e.scheduledByUserEmail = :scheduledByUserEmail,
                  e.scheduledStartRetryCount = 0,
                  e.scheduledStartNextRetryAt = null
              WHERE e.id = :id AND e.teamId = :teamId
                AND e.status = com.alertops.flow_execution_engine.model.EscalationStatus.SCHEDULED
              """)
      int rescheduleScheduled(
              @Param("id") UUID id,
              @Param("teamId") UUID teamId,
              @Param("scheduledStartAt") Instant scheduledStartAt,
              @Param("scheduleTimezone") String scheduleTimezone,
              @Param("scheduledByUserId") UUID scheduledByUserId,
              @Param("scheduledByUserEmail") String scheduledByUserEmail);
}
