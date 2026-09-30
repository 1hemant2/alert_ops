package com.alertops.flow_execution_engine.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, UUID> {
      Page<Escalation> findByTeamId(UUID teamId, Pageable pageable);

      @Query("select e from Escalation e where e.id = :id and e.teamId = :teamId")
      Escalation findByIdAndTeamId(UUID id, UUID teamId);

      @Lock(LockModeType.PESSIMISTIC_WRITE)
      @Query("select e from Escalation e where e.id = :id")
      Optional<Escalation> findByIdForUpdate(@Param("id") UUID id);

      @Modifying
      @Query("""
              UPDATE Escalation e
              SET e.status = 'RUNNING'
              WHERE e.id = :id AND e.teamId = :teamId AND e.status = 'IDLE'
              """)
      int claimForStart(@Param("id") UUID id, @Param("teamId") UUID teamId);

      List<Escalation> findAllByStatus(String status);
}
