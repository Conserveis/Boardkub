package th.ac.kmitl.connect.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-window counters in Redis (Memorystore on Google Cloud).
 * Every backend Pod shares the same counters, so a limit holds no matter which
 * Pod the load balancer picks — the same reason sessions live in Redis.
 */
@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis", matchIfMissing = true)
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        // Create the key with its TTL first, so a counter can never live forever
        redis.opsForValue().setIfAbsent(key, "0", window);
        Long v = redis.opsForValue().increment(key);
        return v == null ? 0 : v;
    }

    @Override
    public long get(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0 : Long.parseLong(v);
    }

    @Override
    public long secondsLeft(String key) {
        Long s = redis.getExpire(key, TimeUnit.SECONDS);
        return s == null || s < 0 ? 0 : s;
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
