package com.novax.leadora.unit.security;

import com.novax.leadora.common.security.OtpStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The OTP flows must survive a stack with no Redis in it — {@code .env} ships the Redis block
 * commented out, and before {@link OtpStore} existed that turned
 * {@code POST /public/quotations/{id}/request-otp} into a 500 {@code REDIS_CONNECTION_FAILED},
 * blocking customer acceptance and contract signing entirely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpStore otpStore;

    /** Makes every Redis call fail the way an absent server does. */
    private void givenRedisIsDown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"));
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"));
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"));
    }

    @Test
    @DisplayName("issues and reads back a code with no Redis running")
    void survivesRedisBeingDown() {
        givenRedisIsDown();

        otpStore.put("quotation_otp:1", "123456", Duration.ofMinutes(5));

        assertThat(otpStore.get("quotation_otp:1")).isEqualTo("123456");
    }

    @Test
    @DisplayName("an expired code reads back as absent, so the caller reports OTP_EXPIRED")
    void expiresLocally() {
        givenRedisIsDown();

        otpStore.put("quotation_otp:2", "123456", Duration.ZERO);

        assertThat(otpStore.get("quotation_otp:2")).isNull();
    }

    @Test
    @DisplayName("a consumed code cannot be replayed")
    void deleteIsSingleUse() {
        givenRedisIsDown();

        otpStore.put("quotation_otp:3", "123456", Duration.ofMinutes(5));
        otpStore.delete("quotation_otp:3");

        assertThat(otpStore.get("quotation_otp:3")).isNull();
    }

    @Test
    @DisplayName("the failed-attempt ceiling still counts up without Redis")
    void countsFailedAttemptsLocally() {
        givenRedisIsDown();

        assertThat(otpStore.increment("quotation_otp_fail:4", Duration.ofMinutes(15))).isEqualTo(1L);
        assertThat(otpStore.increment("quotation_otp_fail:4", Duration.ofMinutes(15))).isEqualTo(2L);
        assertThat(otpStore.increment("quotation_otp_fail:4", Duration.ofMinutes(15))).isEqualTo(3L);
    }

    @Test
    @DisplayName("unknown keys read as absent rather than throwing")
    void missingKeyReadsAsNull() {
        givenRedisIsDown();

        assertThat(otpStore.get("quotation_otp:never-issued")).isNull();
    }

    @Test
    @DisplayName("Redis remains the store while it is reachable")
    void prefersRedisWhenAvailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("quotation_otp:5")).thenReturn("654321");

        otpStore.put("quotation_otp:5", "654321", Duration.ofMinutes(5));

        assertThat(otpStore.get("quotation_otp:5")).isEqualTo("654321");
        verify(valueOperations).set("quotation_otp:5", "654321", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("a code issued during an outage is still found after Redis recovers")
    void readsThroughToLocalAfterRecovery() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        otpStore.put("quotation_otp:6", "123456", Duration.ofMinutes(5));

        // Redis is back, and answers a miss for a key it never received.
        when(valueOperations.get("quotation_otp:6")).thenReturn(null);

        assertThat(otpStore.get("quotation_otp:6")).isEqualTo("123456");
    }
}
