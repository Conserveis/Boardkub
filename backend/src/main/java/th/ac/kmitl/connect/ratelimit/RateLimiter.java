package th.ac.kmitl.connect.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import th.ac.kmitl.connect.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Per-account limits inside the application. Per-IP limits are handled earlier by
 * Cloud Armor; per-account limits are what stop an attacker who spreads requests
 * over many IPs, and they never block a whole dorm sharing one public IP.
 *
 * <p>Keys hold a SHA-256 hash of the email / user id, so Redis stores no personal data.
 */
@Component
public class RateLimiter {

    public enum Limit {
        /** Sending an OTP email (register + resend), per email. */
        OTP_SEND("otp-send", 3, 15),
        /** Wrong OTP codes, per email: 5 tries out of 1,000,000 codes. */
        OTP_VERIFY("otp-verify", 5, 15),
        /** Wrong passwords, per email. */
        LOGIN("login", 5, 15),
        /** New posts, per user. */
        POST_CREATE("post-create", 5, 10),
        /** New comments, per user. */
        COMMENT_CREATE("comment-create", 20, 10);

        final String slug;
        final int defaultMax;
        final int defaultWindowMinutes;

        Limit(String slug, int defaultMax, int defaultWindowMinutes) {
            this.slug = slug;
            this.defaultMax = defaultMax;
            this.defaultWindowMinutes = defaultWindowMinutes;
        }
    }

    private record Rule(int max, Duration window) {}

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitStore store;
    private final Map<Limit, Rule> rules = new EnumMap<>(Limit.class);

    public RateLimiter(RateLimitStore store, Environment env) {
        this.store = store;
        // Overridable per limit, e.g. app.rate-limit.post-create.max=10
        for (Limit l : Limit.values()) {
            int max = env.getProperty("app.rate-limit." + l.slug + ".max", Integer.class, l.defaultMax);
            int minutes = env.getProperty("app.rate-limit." + l.slug + ".window-minutes", Integer.class,
                    l.defaultWindowMinutes);
            rules.put(l, new Rule(max, Duration.ofMinutes(minutes)));
        }
    }

    /** Counts this action and rejects it once the limit is passed. */
    public void consume(Limit limit, Object subject) {
        String key = key(limit, subject);
        try {
            long n = store.increment(key, rules.get(limit).window());
            if (n > rules.get(limit).max()) {
                throw tooMany(store.secondsLeft(key));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            failOpen(limit, e);
        }
    }

    /** Rejects the action if earlier failures already reached the limit. */
    public void ensureAllowed(Limit limit, Object subject) {
        String key = key(limit, subject);
        try {
            if (store.get(key) >= rules.get(limit).max()) {
                throw tooMany(store.secondsLeft(key));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            failOpen(limit, e);
        }
    }

    /** Counts one failed attempt (wrong password, wrong OTP). */
    public void recordFailure(Limit limit, Object subject) {
        try {
            store.increment(key(limit, subject), rules.get(limit).window());
        } catch (RuntimeException e) {
            failOpen(limit, e);
        }
    }

    /** Clears the counter, e.g. after a successful login. */
    public void reset(Limit limit, Object subject) {
        try {
            store.delete(key(limit, subject));
        } catch (RuntimeException e) {
            failOpen(limit, e);
        }
    }

    private static ApiException tooMany(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return ApiException.tooManyRequests(
                "ทำรายการบ่อยเกินไป กรุณาลองใหม่ในอีกประมาณ " + minutes + " นาที", Math.max(1, seconds));
    }

    /**
     * If Redis is unreachable the site keeps working without limits rather than
     * refusing every login; Cloud Armor still limits per IP at the edge.
     */
    private static void failOpen(Limit limit, RuntimeException e) {
        log.warn("Rate limit store unavailable for {}: {}", limit.slug, e.getMessage());
    }

    private static String key(Limit limit, Object subject) {
        return "kmitl:rl:" + limit.slug + ":" + hash(String.valueOf(subject).trim().toLowerCase());
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
