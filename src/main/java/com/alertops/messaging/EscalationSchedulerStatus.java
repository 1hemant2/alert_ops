package com.alertops.messaging;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "scheduling")
public class EscalationSchedulerStatus {
    private final StepTimerRegistry timerRegistry;
    private final StepSchedulingService schedulingService;
    private final ReconcilerService reconcilerService;

    public EscalationSchedulerStatus(
            StepTimerRegistry timerRegistry,
            StepSchedulingService schedulingService,
            ReconcilerService reconcilerService
    ) {
        this.timerRegistry = timerRegistry;
        this.schedulingService = schedulingService;
        this.reconcilerService = reconcilerService;
    }

    @ReadOperation
    public SchedulingStatus status() {
        return new SchedulingStatus(
                timerRegistry.activeTimerCount(),
                schedulingService.countPendingSchedules(),
                reconcilerService.isRecoveryComplete());
    }

    public record SchedulingStatus(int activeTimers, long pendingSchedules, boolean recoveryComplete) {
    }
}
