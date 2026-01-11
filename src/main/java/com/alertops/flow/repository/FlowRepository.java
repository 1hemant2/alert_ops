package com.alertops.flow.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.alertops.flow.model.Flow;

@Repository
public interface FlowRepository extends JpaRepository<Flow, UUID> {
        Page<Flow> findByTeamId(UUID teamId, Pageable pageable);

        @Modifying
        @Query("""
                UPDATE Flow f SET f.name = :#{#flow.name}, f.updatedBy = :#{#flow.updatedBy}, f.updatedAt = :#{#flow.updatedAt}
                WHERE f.id = :#{#flow.id} AND f.teamId = :#{#flow.teamId}
                """
        )
        void updateFlow(@Param("flow") Flow flow);

}
