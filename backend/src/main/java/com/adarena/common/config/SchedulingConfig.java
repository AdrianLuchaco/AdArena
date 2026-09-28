package com.adarena.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Activa las tareas programadas (@Scheduled): ver common/jobs/ScheduledJobs.
 * <p>
 * Usan sus PROPIOS hilos ("adarena-jobs-N"): sin esto, Spring las ejecutaría en los hilos del
 * WebSocket, y un email lento podría retrasar el latido de las conexiones en directo o el cierre.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("adarena-jobs-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
