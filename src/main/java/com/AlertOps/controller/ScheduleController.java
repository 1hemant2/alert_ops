package com.AlertOps.controller;

// import com.example.scheduler.model.ScheduledTask;
// import com.example.scheduler.repository.ScheduledTaskRepository;
// import com.example.scheduler.service.MessagePublisher;
// import com.example.scheduler.dto.ScheduledEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.AlertOps.dto.Schedular.ScheduledEvent;
import com.AlertOps.model.ScheduledTask;
import com.AlertOps.repository.ScheduledTaskRepository;
import com.AlertOps.service.MessagePublisher;

import java.time.Instant;
import java.util.List;


@RestController
@RequestMapping("/api/v1/scheduler")
    public class ScheduleController {


    private final ScheduledTaskRepository repo;
    private final MessagePublisher publisher;


    public ScheduleController(ScheduledTaskRepository repo, MessagePublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }


    record ScheduleReq(String email, String message, Instant nextFireAt) {}


    @PostMapping("/bulk")
    public ResponseEntity<?> scheduleBulk(@RequestBody List<ScheduleReq> list) {
        for (ScheduleReq r : list) {
            ScheduledTask t = new ScheduledTask();
            t.setEmail(r.email());
            t.setMessage(r.message());
            t.setNextFireAt(r.nextFireAt());
            t.setStatus("PENDING");
            t.setQueued(false);
            repo.save(t);
            long delay = Math.max(0, r.nextFireAt().toEpochMilli() - Instant.now().toEpochMilli());
            publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), delay);
            t.setQueued(true);
            t.setStatus("QUEUED");
            repo.save(t);
        }
        return ResponseEntity.ok().build();
    }
}
