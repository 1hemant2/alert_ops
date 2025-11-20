package com.AlertOps.repository;

import com.AlertOps.model.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

}
