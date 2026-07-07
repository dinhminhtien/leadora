package com.novax.leadora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async infrastructure. Currently used only by RAG document ingestion: parsing + embedding
 * a company document can take minutes — longer than a browser keeps an HTTP request open
 * (~5 min in Chrome). The upload endpoint therefore accepts the file, returns immediately,
 * and hands the heavy work to this executor (see {@code DocumentIngestService}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Single-threaded on purpose: ingestion is bottlenecked by the Gemini embedding quota
     * (free tier), so running uploads sequentially avoids competing for it. The queue
     * absorbs bursts; each queued entry holds the uploaded file's bytes.
     */
    @Bean(name = "documentIngestExecutor")
    public Executor documentIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("doc-ingest-");
        executor.initialize();
        return executor;
    }

    /**
     * General task executor for concurrent background tasks (like async logging or email send-offs)
     * so that fast best-effort tasks do not get queued behind the slow doc-ingest tasks.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
}
