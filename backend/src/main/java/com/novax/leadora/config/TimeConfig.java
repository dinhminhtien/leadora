package com.novax.leadora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Where the application's notion of "now" comes from.
 *
 * <p><b>Why a bean rather than {@code Instant.now()} at each call site.</b> Time read straight from
 * the JVM cannot be pinned, so the moments most likely to be wrong are the ones that cannot be
 * tested: the second either side of midnight, the Sunday that becomes a Monday, 31 December, 29
 * February. Those either get verified by waiting for them to happen — which nobody does — or not at
 * all. Behind this bean they are ordinary unit tests.
 *
 * <p><b>This is also the seam for an external time source.</b> Should the company want time from
 * somewhere other than the host clock, replace this bean; nothing that reads a clock has to change.
 * The one thing such an implementation must not do is call out over the network on each read —
 * {@code ChatClock} is consulted on every chat turn, ahead of the first token, so an HTTP round trip
 * there would be paid by every question asked. Sample the remote source on a schedule, keep the
 * offset, and serve reads from {@code Clock.offset(Clock.systemUTC(), drift)}.
 */
@Configuration
public class TimeConfig {

    /**
     * The host clock, in UTC.
     *
     * <p>UTC rather than the platform default on purpose: this bean supplies the <em>instant</em>
     * only, and every caller decides for itself which calendar to read that instant against.
     * Leaving a zone on it invites code to inherit whatever the container happens to be set to,
     * which is the exact bug that made "today" answer for the wrong day.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
