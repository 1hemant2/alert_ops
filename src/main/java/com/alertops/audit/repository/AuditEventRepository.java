package com.alertops.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.alertops.audit.model.AuditEventEntity;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    // Loads one entity's audit history in the caller's requested stable page.
    Page<AuditEventEntity> findAllByEntityTypeAndEntityId(
            String entityType, UUID entityId, Pageable pageable);

    List<AuditEventEntity> findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
            String entityType, UUID entityId);
}
