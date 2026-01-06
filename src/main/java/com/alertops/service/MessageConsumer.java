/*
package com.alertops.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.configuration.RabbitMqConfig;
import com.alertops.dto.schedular.ScheduledEvent;
import com.alertops.model.ScheduledTask;
import com.alertops.repository.ScheduledTaskRepository;


import java.util.Optional;


@Service
public class MessageConsumer {
    private final MessageExecutor executor;
    private final ScheduledTaskRepository repo;
   // private final MessagePublisher publisher;


    public MessageConsumer(MessageExecutor executor, ScheduledTaskRepository repo, MessagePublisher publisher) {
        this.executor = executor;
        this.repo = repo;
      //  this.publisher = publisher;
    }


    @RabbitListener(queues = RabbitMqConfig.FINAL_QUEUE)
    @Transactional(readOnly = false)
    public void onMessage(ScheduledEvent event) {
        process(event.getId(), event.getEmail(), event.getMessage());
    }


    @Transactional(readOnly = false, propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public void process(Long taskId, String email, String message) {
        Optional<ScheduledTask> opt = repo.findById(taskId);
        if (opt.isEmpty()) return; // nothing to do
        ScheduledTask task = opt.get();
        // Idempotency: if already DONE or PROCESSING, skip
        if ("DONE".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus())) {
            return;
        }

        // mark processing
        task.setStatus("PROCESSING");
        repo.save(task);

        boolean sent = false;
        try {
            sent = executor.send(email, message);
        } catch (Exception ex) {
            task.setAttempts(task.getAttempts() + 1);
            task.setLastError(ex.getMessage());
            task.setStatus("FAILED");
            repo.save(task);
            return;
        }

        if (sent) {
            task.setStatus("DONE");
            task.setAttempts(task.getAttempts() + 1);
            repo.save(task);
        }
    }
}*/
