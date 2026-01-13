package com.alertops.flow_execution_engine.service;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EscalationService {
   private EscalationRepository escalationRepository;

    EscalationService (EscalationRepository escalationRepository) {
       this.escalationRepository = escalationRepository;
    }

    public Escalation createEscalation(String name, UUID taskId, UUID flowId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();

            Escalation escalation = new Escalation();
            escalation.setName(name);
            escalation.setTaskId(taskId);
            escalation.setFlowId(flowId);
            escalation.setStatus("IDLE");
            escalation.setTeamId(teamId);
            escalation.setResolutionType(null);
            escalationRepository.save(escalation);
            return escalation;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    public List<Escalation> getEscalations(int page, int size, String sortBy, String sortDir) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Set<String> allowedSortBy = Set.of("taskName", "createdAt");
            Set<String> allowedSortDir = Set.of("asc", "desc");

            if(page < 0) {
                page = 0;
            }

            if(!allowedSortBy.contains(sortBy)) {
                sortBy = "createdAt";
            }

            if(!allowedSortDir.contains(sortDir)) {
                sortDir = "asc";
            }

            Sort sort = Sort.by(Sort.Direction.valueOf(sortDir.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Escalation> escalationPage = escalationRepository.findByTeamId(teamId, pageable);
            List<Escalation> escalations = escalationPage.getContent();

            return escalations;
        } catch (Exception e) {
            throw e;
        }
    }

    public Escalation getEscalationById(UUID escalationId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, teamId);

            if(escalation != null) {
                return escalation;
            } else {
                return null;
            }

        } catch (Exception e) {
            throw e;
        }
    }

    public Escalation updateEscalationStatus(UUID escalationId, String status, String resolutionType, String issueSolvedBy) {
        try {
            Escalation escalation = getEscalationById(escalationId);
            if(escalation == null) {
                return null;
            }
            escalation.setStatus(status);
            escalation.setResolutionType(resolutionType);
            escalation.setIssueSolvedBy(issueSolvedBy);
            escalationRepository.save(escalation);
            return escalation;
        } catch (Exception e) {
            throw e;
        }
    }

}

