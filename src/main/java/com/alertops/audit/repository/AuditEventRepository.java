package com.alertops.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.alertops.audit.model.AuditEventEntity;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
            String entityType, UUID entityId);
}
