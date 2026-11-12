package com.workspace.run.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** In-memory cancel flags of runs being executed (single instance, brief section 10). */
@Component
class RunRegistry {

    private final Map<UUID, AtomicBoolean> cancel = new ConcurrentHashMap<>();

    AtomicBoolean register(UUID runId) {
        return cancel.computeIfAbsent(runId, k -> new AtomicBoolean(false));
    }

    boolean requestCancel(UUID runId) {
        AtomicBoolean flag = cancel.get(runId);
        if (flag == null) return false;
        flag.set(true);
        return true;
    }

    void unregister(UUID runId) {
        cancel.remove(runId);
    }
}
