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
     * Drives streamed chat replies: one thread is held for the length of a whole answer.
     *
     * <p>Deliberately <b>not</b> {@code taskExecutor}. A streaming turn asks
     * {@code ContextAssembler} to gather its sources, which submits to {@code taskExecutor} and
     * waits for the results. Running both on one pool means that once every thread is occupied by
     * a streaming turn, the context tasks they are all waiting for sit in the queue behind them
     * and nothing can finish — a textbook pool deadlock, and one that only appears under load.
     * Separate pools make it impossible.
     */
    @Bean(name = "chatStreamExecutor")
    public Executor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(24);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-stream-");
        // Better to answer slowly on the caller's thread than to reject the message outright.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
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
