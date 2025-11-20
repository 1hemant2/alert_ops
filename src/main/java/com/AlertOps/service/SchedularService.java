package com.AlertOps.service;

import com.AlertOps.dto.Schedular.ScheduledEvent;
import com.AlertOps.model.ScheduledTask;
import com.AlertOps.repository.ScheduledTaskRepository;
import com.AlertOps.dto.Schedular.SheduleReq;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SchedularService {
    private final ScheduledTaskRepository repo;
    private final MessagePublisher publisher;


    public SchedularService(ScheduledTaskRepository repo, MessagePublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    public void startScheduleBulk(List<SheduleReq> list) {
        int delay = 0;
        for (SheduleReq r : list) {
            ScheduledTask t = new ScheduledTask();
            t.setEmail(r.getEmail());
            t.setMessage(r.getMessage());
            t.setNextFireAt(r.getDelay());
            t.setStatus("PENDING");
            t.setQueued(false);
            repo.save(t);
            delay += r.getDelay();
            publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), delay);
            t.setQueued(true);
            t.setStatus("QUEUED");
            repo.save(t);
        }
    }
}

