package com.AlertOps.repository;

import com.AlertOps.model.Escalation;
import com.AlertOps.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

}
