package th.ac.kmitl.connect.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Counters in this JVM only. For tests: with several Pods each would count separately. */
@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "memory")
public class InMemoryRateLimitStore implements RateLimitStore {

    private record Window(long count, Instant expiresAt) {}

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration window) {
        Instant now = Instant.now();
        return windows.compute(key, (k, w) -> (w == null || !w.expiresAt().isAfter(now))
                ? new Window(1, now.plus(window))
                : new Window(w.count() + 1, w.expiresAt())).count();
    }

    @Override
    public long get(String key) {
        Window w = live(key);
        return w == null ? 0 : w.count();
    }

    @Override
    public long secondsLeft(String key) {
        Window w = live(key);
        return w == null ? 0 : Math.max(1, Duration.between(Instant.now(), w.expiresAt()).toSeconds());
    }

    @Override
    public void delete(String key) {
        windows.remove(key);
    }

    private Window live(String key) {
        Window w = windows.get(key);
        if (w != null && !w.expiresAt().isAfter(Instant.now())) {
            windows.remove(key, w);
            return null;
        }
        return w;
    }
}
