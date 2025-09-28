package com.AlertOps.service;

import com.AlertOps.dto.Escalation.EscalationDto;
import com.AlertOps.dto.Escalation.EscalationResDto;
import com.AlertOps.model.Escalation;
import com.AlertOps.model.EscalationStep;
import com.AlertOps.model.User;
import com.AlertOps.repository.EscalationRepository;
import com.AlertOps.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EscalationService {
   private EscalationRepository escalationRepository;
   private UserRepository userRepository;
   private EscalationResDto escalationResDto;

    EscalationService (EscalationRepository escalationRepository, UserRepository userRepository) {
       this.escalationRepository = escalationRepository;
       this.userRepository = userRepository;
   }

    public int createEscalation(
            String flowName,
            Long ownerId,
            List<Long> taskAssigneeIds,
            boolean isStartFlow,
            String startTime
    ) {
        try {
            // Owner of escalation flow
            User flowOwner = userRepository.findById(ownerId)
                    .orElseThrow(() -> new RuntimeException("Owner user not found"));

            // Fetch assignees in order
            List<User> assignees = userRepository.findAllById(taskAssigneeIds);
            Map<Long, User> userMap = assignees.stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            List<User> orderedAssignees = taskAssigneeIds.stream()
                    .map(userMap::get)
                    .toList();

            // Build escalation
            Escalation escalation = new Escalation();
            escalation.setFlowName(flowName);
            escalation.setOwner(flowOwner);
            escalation.setStartFlow(isStartFlow);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
            OffsetDateTime odt = OffsetDateTime.parse(startTime, formatter);
            escalation.setStartTime(odt.toInstant());

            // Add steps (with order)
            List<EscalationStep> steps = new ArrayList<>();
            int order = 1;
            for (User u : orderedAssignees) {
                EscalationStep step = new EscalationStep();
                step.setStepOrder(order++);
                step.setEscalation(escalation);
                step.setUser(u);
                steps.add(step);
            }
            escalation.setSteps(steps);
            // Persist escalation + cascade saves steps
            escalationRepository.save(escalation);
            return 1;

        } catch (RuntimeException e) {
            log.error("❌ Error while creating escalation: {}", e.getMessage(), e);
            return 0;
        }
    }

}
