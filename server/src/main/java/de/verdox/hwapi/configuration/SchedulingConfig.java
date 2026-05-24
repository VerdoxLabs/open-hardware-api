package de.verdox.hwapi.configuration;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    /**
     * Scheduler-Threadpool:
     * - führt NICHT die Arbeit aus, sondern triggert nur @Scheduled Methoden
     * - sollte klein sein, sonst startet er zu viele Jobs gleichzeitig
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    /**
     * Worker-Executor:
     * - hier läuft die teure Arbeit (Scraping, DB, IO)
     * - bounded queue + CallerRunsPolicy = Backpressure (Performance & Stabilität)
     */
    @Bean(name = "jobExecutor")
    public TaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setThreadNamePrefix("job-");

        exec.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        exec.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);

        exec.setQueueCapacity(200);

        // Backpressure: wenn voll, läuft’s im Caller-Thread (verlangsamt Trigger)
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        exec.setAwaitTerminationSeconds(60);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.initialize();
        return exec;
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}