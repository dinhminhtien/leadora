package com.novax.leadora.common.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private boolean redisAvailable = true;
    private long lastRedisRetryTime = 0;
    private static final long REDIS_RETRY_INTERVAL_MS = 60000; // 1 minute

    /**
     * Adds a token to the blacklist with a TTL matching the token's expiration time.
     * If Redis is down, it fails open gracefully and logs the error.
     */
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!redisAvailable) {
            long now = System.currentTimeMillis();
            if (now - lastRedisRetryTime < REDIS_RETRY_INTERVAL_MS) {
                return; // Redis is down, skip to avoid thread blocking
            }
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();

            if (expirationTime != null) {
                long remainingMillis = expirationTime.getTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    String key = BLACKLIST_PREFIX + token;
                    redisTemplate.opsForValue().set(key, "blacklisted", remainingMillis, TimeUnit.MILLISECONDS);
                    log.info("Token successfully blacklisted for {} ms", remainingMillis);
                    if (!redisAvailable) {
                        log.info("Redis connection recovered. Re-enabling TokenBlacklistService.");
                        redisAvailable = true;
                    }
                }
            }
        } catch (Exception e) {
            handleRedisFailure(e, "Failed to blacklist token in Redis");
        }
    }

    /**
     * Checks if a token is in the blacklist.
     * If Redis is down, it fails open gracefully (returns false) and logs the error.
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (!redisAvailable) {
            long now = System.currentTimeMillis();
            if (now - lastRedisRetryTime < REDIS_RETRY_INTERVAL_MS) {
                return false; // Skip to avoid blocking request thread & log spamming
            }
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            boolean result = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            if (!redisAvailable) {
                log.info("Redis connection recovered. Re-enabling TokenBlacklistService.");
                redisAvailable = true;
            }
            return result;
        } catch (Exception e) {
            handleRedisFailure(e, "Failed to check token blacklist in Redis");
            return false;
        }
    }

    private void handleRedisFailure(Exception e, String message) {
        if (redisAvailable) {
            log.error("{}. Disabling Redis checks for {} ms. Gracefully failing open. Error: {}", 
                    message, REDIS_RETRY_INTERVAL_MS, e.getMessage(), e);
            redisAvailable = false;
            lastRedisRetryTime = System.currentTimeMillis();
        } else {
            // Update retry time to back off further calls
            lastRedisRetryTime = System.currentTimeMillis();
        }
    }
}
