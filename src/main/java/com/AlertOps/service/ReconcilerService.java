package com.AlertOps.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AlertOps.dto.Schedular.ScheduledEvent;
import com.AlertOps.model.ScheduledTask;
import com.AlertOps.repository.ScheduledTaskRepository;

import java.time.Instant;
import java.util.List;


@Service
public class ReconcilerService {
    private final ScheduledTaskRepository repo;
    private final MessagePublisher publisher;
    public ReconcilerService(ScheduledTaskRepository repo, MessagePublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }


    @PostConstruct
    public void onStartup() {
        reconcileOnce();
    }


    @Scheduled(fixedRate = 60_000) // every minute
    public void periodic() {
        reconcileOnce();
    }


    @Transactional
    public void reconcileOnce() {
        List<ScheduledTask> pending = repo.findPendingToPublish(List.of("PENDING", "QUEUED"));
        Instant now = Instant.now();


        for (ScheduledTask t : pending) {
            if (t.isQueued()) continue; // already published
            long delay = Math.max(0, t.getNextFireAt().toEpochMilli() - now.toEpochMilli());
            publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), delay);
            t.setQueued(true);
            t.setStatus("QUEUED");
            repo.save(t);
        }


        // Also handle delayed messages that are due but perhaps lost: optional
        List<ScheduledTask> dueButQueued = repo.findByStatusAndNextFireAtBefore("QUEUED", now);
        for (ScheduledTask t : dueButQueued) {
            publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), 0);
        }
    }
}
