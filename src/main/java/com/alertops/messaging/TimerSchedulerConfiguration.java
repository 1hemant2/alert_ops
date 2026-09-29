package com.alertops.messaging;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TimerSchedulerConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public ThreadPoolTaskScheduler taskScheduler(
            // Number of timer callbacks that can run at the same time.
            @Value("${spring.task.scheduling.pool.size:2}") int poolSize
    ) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("The timer thread count must be positive");
        }
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Allows several due timers to be processed in parallel.
        scheduler.setPoolSize(poolSize);
        // Makes timer threads easy to find in logs and thread dumps.
        scheduler.setThreadNamePrefix("alertops-timer-");
        // Removes cancelled timers from the scheduler's waiting list.
        scheduler.setRemoveOnCancelPolicy(true);
        // Do not wait for timer callbacks when the application shuts down.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
