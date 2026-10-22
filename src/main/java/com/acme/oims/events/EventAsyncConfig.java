package com.acme.oims.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class EventAsyncConfig {

    /** async=false runs listeners on the caller's thread (used by tests and for the "before" run). */
    @Bean(name = "eventExecutor")
    Executor eventExecutor(@Value("${oims.events.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor();
        }

        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("event-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // back-pressure, no silent drops
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        return ex;
    }
}