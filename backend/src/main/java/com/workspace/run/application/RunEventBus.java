package com.workspace.run.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE publisher per run (single instance). Deltas are NOT replayed. Terminal events end the stream.
 * Events: run.started, delta, run.completed, run.failed, run.blocked.
 */
@Component
public class RunEventBus {

    private static final Logger log = LoggerFactory.getLogger(RunEventBus.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID runId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    public void publish(UUID runId, String name, Map<String, Object> data) {
        for (SseEmitter e : emitters.getOrDefault(runId, List.of())) send(runId, e, name, data);
    }

    /** Sends one event to one emitter (used to replay the terminal state to late subscribers). */
    public void sendTo(UUID runId, SseEmitter emitter, String name, Map<String, Object> data) {
        send(runId, emitter, name, data);
    }

    public void publishTerminal(UUID runId, String name, Map<String, Object> data) {
        List<SseEmitter> list = emitters.remove(runId);
        if (list == null) return;
        for (SseEmitter e : list) {
            send(runId, e, name, data);
            e.complete();
        }
    }

    @Scheduled(fixedDelay = 15_000)
    void keepAlive() {
        for (List<SseEmitter> list : emitters.values()) {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException ex) {
                    list.remove(e);
                }
            }
        }
    }

    private void send(UUID runId, SseEmitter e, String name, Map<String, Object> data) {
        try {
            e.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Dropping a closed SSE subscriber of run {}", runId);
            List<SseEmitter> list = emitters.get(runId);
            if (list != null) list.remove(e);
        }
    }
}
