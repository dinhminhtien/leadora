package com.novax.leadora.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for the short-lived one-time codes behind the customer-facing quotation and contract
 * acceptance flows (UC-14.x).
 *
 * <p>Redis stays the preferred store — it is shared by every instance and expires keys itself — but
 * it is <b>optional</b> infrastructure here: {@code .env} ships its block commented out, there is no
 * compose file that starts it, and every other consumer in this codebase degrades rather than fails
 * ({@link TokenBlacklistService} fails open on a backoff, {@code CacheConfig} falls back to the
 * database through its {@code CacheErrorHandler}). OTP issuing was the one exception, and it failed
 * closed: with no Redis listening, {@code POST /public/quotations/{id}/request-otp} answered 500
 * {@code REDIS_CONNECTION_FAILED}, so the customer could not accept a quotation — and could not
 * reach contract signing, which fails the same way — without someone first running a Redis server.
 *
 * <p>When Redis is unreachable the codes live in this process instead. None of the checks relax:
 * same TTL, same attempt ceiling, same single-use deletion on success. What is lost is the shared
 * store — codes do not survive a restart, and behind more than one instance a code must be
 * confirmed by the instance that issued it. Point {@code REDIS_URL} at a real Redis when that
 * matters; this class will use it again automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpStore {

    private final StringRedisTemplate redisTemplate;

    /** Codes issued while Redis was unreachable. Keyed exactly as the Redis keys are. */
    private final Map<String, LocalEntry> localEntries = new ConcurrentHashMap<>();

    private volatile boolean redisAvailable = true;
    private volatile long lastRedisFailureTime = 0L;
    private static final long REDIS_RETRY_INTERVAL_MS = 60_000;

    /** Stores {@code value} under {@code key}, replacing any previous code, for {@code ttl}. */
    public void put(String key, String value, Duration ttl) {
        sweepExpired();
        if (shouldTryRedis()) {
            try {
                redisTemplate.opsForValue().set(key, value, ttl);
                markRedisUp();
                return;
            } catch (Exception e) {
                markRedisDown(e, "store an OTP");
            }
        }
        localEntries.put(key, LocalEntry.of(value, ttl));
    }

    /** The stored value, or {@code null} once it has expired, been consumed, or never existed. */
    public String get(String key) {
        if (shouldTryRedis()) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                markRedisUp();
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                markRedisDown(e, "read an OTP");
            }
        }
        // Consulted even when Redis answered a miss: the code may have been issued into this
        // process during an outage that has since recovered.
        LocalEntry entry = localEntries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            localEntries.remove(key);
            return null;
        }
        return entry.value();
    }

    /**
     * Increments the counter at {@code key} and returns its new value, refreshing its TTL — the
     * failed-attempt ceiling that locks a code after too many wrong guesses.
     */
    public long increment(String key, Duration ttl) {
        if (shouldTryRedis()) {
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                redisTemplate.expire(key, ttl);
                markRedisUp();
                if (count != null) {
                    return count;
                }
            } catch (Exception e) {
                markRedisDown(e, "count a failed OTP attempt");
            }
        }
        LocalEntry current = localEntries.get(key);
        long next = current != null && !current.isExpired() ? parseCount(current.value()) + 1 : 1;
        localEntries.put(key, LocalEntry.of(Long.toString(next), ttl));
        return next;
    }

    /** Discards a code, whichever store holds it. */
    public void delete(String key) {
        if (shouldTryRedis()) {
            try {
                redisTemplate.delete(key);
                markRedisUp();
            } catch (Exception e) {
                markRedisDown(e, "discard an OTP");
            }
        }
        localEntries.remove(key);
    }

    /**
     * Whether to spend this request on a Redis round trip. After a failure the attempts pause for
     * {@link #REDIS_RETRY_INTERVAL_MS} so a Redis-less stack does not pay a connection refusal —
     * and log one — on every OTP operation.
     */
    private boolean shouldTryRedis() {
        return redisAvailable
                || System.currentTimeMillis() - lastRedisFailureTime >= REDIS_RETRY_INTERVAL_MS;
    }

    private void markRedisUp() {
        if (!redisAvailable) {
            log.info("Redis reachable again — OTP codes are shared once more.");
            redisAvailable = true;
        }
    }

    private void markRedisDown(Exception e, String attemptedAction) {
        lastRedisFailureTime = System.currentTimeMillis();
        if (redisAvailable) {
            redisAvailable = false;
            log.warn("Redis unavailable, could not {}. Falling back to in-process OTP storage for "
                    + "the next {} ms: codes will not survive a restart and are not shared between "
                    + "instances. Error: {}", attemptedAction, REDIS_RETRY_INTERVAL_MS, e.getMessage());
        }
    }

    private static long parseCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Drops expired entries. Redis does this itself; the local map would otherwise hold every code
     * it ever issued, since a code that is never confirmed is never deleted by a caller.
     */
    private void sweepExpired() {
        localEntries.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private record LocalEntry(String value, long expiresAtMillis) {

        static LocalEntry of(String value, Duration ttl) {
            return new LocalEntry(value, System.currentTimeMillis() + Math.max(ttl.toMillis(), 0L));
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
