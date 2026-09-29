package com.alertops.flow_execution_engine.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.alertops.flow_execution_engine.model.FlowExecutionState;


@Repository
public interface FlowExecutionStateRepository extends JpaRepository<FlowExecutionState, UUID> {
   List<FlowExecutionState> findAllByProcessIdOrderByPositionAsc(UUID processId);
   
   FlowExecutionState findTopByProcessIdOrderByPositionAsc(UUID processId);

   FlowExecutionState findFirstByProcessIdAndExecutionStateOrderByPositionAsc(UUID processId, String status);

   @Query("""
           SELECT state FROM FlowExecutionState state
           WHERE state.executionState = 'ACTIVE'
             AND state.notificationState = 'NOT_SENT'
             AND state.publicationPending = true
             AND state.dueAt IS NOT NULL
             AND EXISTS (
                 SELECT escalation.id FROM Escalation escalation
                 WHERE escalation.id = state.processId AND escalation.status = 'RUNNING'
             )
           ORDER BY state.dueAt ASC, state.id ASC
           """)
   List<FlowExecutionState> findPendingPublications(Pageable pageable);

   @Query("""
           SELECT COUNT(state) FROM FlowExecutionState state
           WHERE state.executionState = 'ACTIVE'
             AND state.notificationState = 'NOT_SENT'
             AND state.publicationPending = true
             AND EXISTS (
                 SELECT escalation.id FROM Escalation escalation
                 WHERE escalation.id = state.processId AND escalation.status = 'RUNNING'
             )
           """)
   long countPendingPublications();

   @Modifying
   @Query("""
           UPDATE FlowExecutionState state
           SET state.publicationPending = false
           WHERE state.id = :stateId
             AND state.executionState = 'ACTIVE'
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
           SET state.executionState = 'PROCESSING', state.publicationPending = false
           WHERE state.id = :stateId
             AND state.executionState = 'ACTIVE'
             AND state.notificationState = 'NOT_SENT'
             AND state.sendAttemptCount = :sendAttemptCount
             AND state.dueAt = :dueAt
           """)
   int claimForDelivery(
           @Param("stateId") UUID stateId,
           @Param("sendAttemptCount") int sendAttemptCount,
           @Param("dueAt") Instant dueAt
   );

}
