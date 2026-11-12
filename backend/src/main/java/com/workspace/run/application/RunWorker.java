package com.workspace.run.application;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In-process worker: polls QUEUED runs (FOR UPDATE SKIP LOCKED) with bounded concurrency. */
@Component
class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final RunStateService states;
    private final RunExecutor executor;
    private final int concurrency;
    private final ExecutorService pool;
    private final AtomicInteger inFlight = new AtomicInteger();

    RunWorker(RunStateService states, RunExecutor executor, @Value("${workspace.run.concurrency:2}") int concurrency) {
        this.states = states;
        this.executor = executor;
        this.concurrency = Math.max(1, concurrency);
        this.pool = Executors.newFixedThreadPool(this.concurrency, r -> {
            Thread t = new Thread(r, "run-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** Runs left RUNNING by a previous process can never finish: FAILED / INTERRUPTED (the user may retry). */
    @EventListener(ApplicationReadyEvent.class)
    void recover() {
        int n = states.failInterrupted();
        if (n > 0) log.warn("{} run(s) were interrupted by a restart and are now FAILED (INTERRUPTED)", n);
    }

    @Scheduled(fixedDelayString = "${workspace.run.poll-ms:1000}")
    void poll() {
        try {
            int free = concurrency - inFlight.get();
            if (free <= 0) return;
            List<UUID> ids = states.claim(free);
            for (UUID id : ids) {
                inFlight.incrementAndGet();
                pool.execute(() -> {
                    try {
                        executor.execute(id);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            }
        } catch (RuntimeException e) {
            log.warn("Run poll failed: {}", e.getClass().getName());
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }
}
