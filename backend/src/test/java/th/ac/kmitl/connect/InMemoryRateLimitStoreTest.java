package th.ac.kmitl.connect;

import org.junit.jupiter.api.Test;
import th.ac.kmitl.connect.ratelimit.InMemoryRateLimitStore;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimitStoreTest {

    @Test
    void windowExpiresAndCounterRestarts() throws Exception {
        InMemoryRateLimitStore s = new InMemoryRateLimitStore();
        assertEquals(1, s.increment("k", Duration.ofMillis(150)));
        assertEquals(2, s.increment("k", Duration.ofMillis(150)));
        assertEquals(2, s.get("k"));
        Thread.sleep(200);
        assertEquals(0, s.get("k"));
        assertEquals(1, s.increment("k", Duration.ofMillis(150)));
        s.delete("k");
        assertEquals(0, s.get("k"));
    }
}
