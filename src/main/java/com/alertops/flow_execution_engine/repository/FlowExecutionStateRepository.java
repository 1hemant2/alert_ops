package com.alertops.flow_execution_engine.repository;

import java.util.List;
import java.util.UUID;

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

   @Modifying
   @Query("""
           UPDATE FlowExecutionState state
           SET state.executionState = 'PROCESSING'
           WHERE state.id = :stateId
             AND state.executionState = 'ACTIVE'
             AND state.notificationState = 'NOT_SENT'
             AND state.sendAttemptCount = :sendAttemptCount
           """)
   int claimForDelivery(
           @Param("stateId") UUID stateId,
           @Param("sendAttemptCount") int sendAttemptCount
   );

}
