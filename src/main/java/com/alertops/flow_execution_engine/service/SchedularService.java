// package com.alertops.flow_execution_engine.service;



// import com.alertops.dto.schedular.ScheduledEvent;
// // import com.alertops.model.ScheduledTask;
// // import com.alertops.repository.ScheduledTaskRepository;
// import com.alertops.dto.schedular.SheduleReq;
// import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
// import com.alertops.messaging.MessagePublisher;

// import java.util.List;
// import org.springframework.stereotype.Service;

// @Service
// public class SchedularService {
//     // private final ScheduledTaskRepository repo;
//     private final MessagePublisher publisher;
//     private final FlowExecutionStateRepository flowExecutionStateRepository;

//     public SchedularService(MessagePublisher publisher, FlowExecutionStateRepository flowExecutionStateRepository) {
//         // this.repo = repo;
//         this.publisher = publisher;
//         this.flowExecutionStateRepository = flowExecutionStateRepository;
//     }

//     public void startScheduleBulk(List<SheduleReq> list) {
//         int delay = 0;
//         for (SheduleReq r : list) {
//             ScheduledTask t = new ScheduledTask();
//             t.setEmail(r.getEmail());
//             t.setMessage(r.getMessage());
//             t.setNextFireAt(r.getDelay());
//             t.setStatus("PENDING");
//             t.setQueued(false);
//             repo.save(t);
//             delay += r.getDelay();
//             publisher.publishWithDelay(new ScheduledEvent(t.getId(), t.getEmail(), t.getMessage()), delay);
//             t.setQueued(true);
//             t.setStatus("QUEUED");
//             repo.save(t);
//         }
//     }
// }
