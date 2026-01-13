package com.alertops.flow_execution_engine.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.alertops.flow_execution_engine.model.Escalation;

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, UUID> {
      Page<Escalation> findByTeamId(UUID teamId, Pageable pageable);

      @Query("select e from Escalation e where e.id = :id and e.teamId = :teamId")
      Escalation findByIdAndTeamId(UUID id, UUID teamId);
}

