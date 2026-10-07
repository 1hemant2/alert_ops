package com.alertops.flow_execution_engine.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;


@Repository
public interface FlowExecutionStateRepository extends JpaRepository<FlowExecutionState, UUID> {
   List<FlowExecutionState> findAllByProcessIdOrderByPositionAsc(UUID processId);
   
   FlowExecutionState findTopByProcessIdOrderByPositionAsc(UUID processId);

   FlowExecutionState findFirstByProcessIdAndStatusOrderByPositionAsc(
           UUID processId, FlowExecutionStepStatus status);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   FlowExecutionState findTopByProcessIdAndStatusOrderByPositionDesc(
           UUID processId, FlowExecutionStepStatus status);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   FlowExecutionState findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
           UUID processId, List<FlowExecutionStepStatus> statuses);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("""
           SELECT state FROM FlowExecutionState state
           WHERE state.processId = :processId
             AND state.status IN (
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.PENDING,
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED,
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.PAUSED
             )
           ORDER BY state.position ASC, state.id ASC
           """)
   List<FlowExecutionState> findUnsentStepsForUpdate(@Param("processId") UUID processId);

   @Query("""
           SELECT state FROM FlowExecutionState state
           WHERE state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED
             AND state.publicationPending = true
             AND state.dueAt IS NOT NULL
             AND EXISTS (
                 SELECT escalation.id FROM Escalation escalation
                 WHERE escalation.id = state.processId
                   AND escalation.status = com.alertops.flow_execution_engine.model.EscalationStatus.OPEN
             )
           ORDER BY state.dueAt ASC, state.id ASC
           """)
   List<FlowExecutionState> findPendingPublications(Pageable pageable);

   @Query("""
           SELECT COUNT(state) FROM FlowExecutionState state
           WHERE state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED
             AND state.publicationPending = true
             AND EXISTS (
                 SELECT escalation.id FROM Escalation escalation
                 WHERE escalation.id = state.processId
                   AND escalation.status = com.alertops.flow_execution_engine.model.EscalationStatus.OPEN
             )
           """)
   long countPendingPublications();

   @Modifying
   @Query("""
           UPDATE FlowExecutionState state
           SET state.publicationPending = false
           WHERE state.id = :stateId
             AND state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED
             AND state.sendAttemptCount = :sendAttemptCount
             AND state.dueAt = :dueAt
             AND state.publicationPending = true
           """)
   int markPublished(
           @Param("stateId") UUID stateId,
           @Param("sendAttemptCount") int sendAttemptCount,
           @Param("dueAt") Instant dueAt
   );

   @Modifying
   @Query("""
           UPDATE FlowExecutionState state
           SET state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SENDING,
               state.publicationPending = false
           WHERE state.id = :stateId
             AND state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED
             AND state.sendAttemptCount = :sendAttemptCount
             AND state.dueAt = :dueAt
           """)
   int claimForDelivery(
           @Param("stateId") UUID stateId,
           @Param("sendAttemptCount") int sendAttemptCount,
           @Param("dueAt") Instant dueAt
   );

   @Modifying
   @Query("""
           UPDATE FlowExecutionState state
           SET state.status = com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SKIPPED,
               state.publicationPending = false,
               state.dueAt = null
           WHERE state.processId = :processId
             AND state.status IN (
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.PENDING,
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.SCHEDULED,
                 com.alertops.flow_execution_engine.model.FlowExecutionStepStatus.PAUSED
             )
           """)
   int markUnsentStepsSkipped(@Param("processId") UUID processId);

}
