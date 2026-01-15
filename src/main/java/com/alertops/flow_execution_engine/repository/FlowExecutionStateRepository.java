package com.alertops.flow_execution_engine.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.alertops.flow_execution_engine.model.FlowExecutionState;

@Repository
public interface FlowExecutionStateRepository extends JpaRepository<FlowExecutionState, UUID> {
   List<FlowExecutionState> findAllByProcessIdOrderByPositionAsc(UUID processId);
   
   FlowExecutionState findTopByProcessIdOrderByPositionAsc(UUID processId);

   FlowExecutionState findFirstByProcessIdAndExecutionStateOrderByPositionAsc(UUID processId, String status);


}
