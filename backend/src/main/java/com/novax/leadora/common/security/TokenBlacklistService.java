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

    /**
     * Adds a token to the blacklist with a TTL matching the token's expiration time.
     * If Redis is down, it fails open gracefully and logs the error.
     */
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
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
                }
            }
        } catch (Exception e) {
            log.error("Failed to blacklist token in Redis. Gracefully failing open.", e);
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
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Failed to check token blacklist in Redis. Gracefully failing open.", e);
            return false;
        }
    }
}
