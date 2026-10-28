package com.workspace.identity.application;

import com.workspace.shared.published.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** In-memory failed-login throttle keyed by normalised email (single instance, MVP). */
@Component
public class LoginThrottle {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final LoginProperties props;
    private final Clock clock;

    public LoginThrottle(LoginProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void checkAllowed(String key) {
        Deque<Instant> d = failures.get(key);
        if (d == null) return;
        synchronized (d) {
            prune(d);
            if (d.size() >= props.maxFailures()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_THROTTLED",
                        "Too many failed login attempts. Try again later.");
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED_KEYS) {
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue());
                    return e.getValue().isEmpty();
                }
            });
        }
        Deque<Instant> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            prune(d);
            d.addLast(clock.instant());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> d) {
        Instant cutoff = clock.instant().minus(props.window());
        while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) d.removeFirst();
    }
}
