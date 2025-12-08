package com.alertops.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.dto.schedular.ScheduledEvent;
import com.alertops.model.ScheduledTask;
import com.alertops.repository.ScheduledTaskRepository;

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

    @Transactional
    public void reconcileOnce() {
        List<ScheduledTask> pending = repo.findPendingToPublish(List.of("PENDING"));


        for (ScheduledTask t : pending) {
            Long delay =  t.getNextFireAt();
            publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), delay);
            t.setQueued(true);
            t.setStatus("QUEUED");
            repo.save(t);
        }
    }
}
