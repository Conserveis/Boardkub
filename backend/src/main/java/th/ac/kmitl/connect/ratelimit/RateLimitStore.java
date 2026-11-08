package th.ac.kmitl.connect.ratelimit;

import java.time.Duration;

/** Counters that expire on their own. Redis in every environment, memory in tests. */
public interface RateLimitStore {

    /** Adds one and returns the new value. The first hit starts a window of the given length. */
    long increment(String key, Duration window);

    /** Current value, 0 if the key does not exist. */
    long get(String key);

    /** Seconds until the window ends, 0 if the key does not exist. */
    long secondsLeft(String key);

    void delete(String key);
}
