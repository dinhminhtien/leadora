package com.novax.leadora.config;

import com.novax.leadora.application.usecase.chat.time.ChatClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the clock actually wires, and that its zone comes from configuration.
 *
 * <p>Worth its own test because nothing else covers it: {@link ChatClock} takes a {@link Clock} in
 * its constructor, and the repository tests run on a {@code @DataJpaTest} slice that loads neither
 * this configuration nor that component. A missing bean would therefore pass the whole suite and
 * fail on the first startup after deployment.
 *
 * <p>Uses {@link ApplicationContextRunner} rather than {@code @SpringBootTest}: the question is
 * whether two beans satisfy each other, which needs no database, no Redis and no model provider.
 */
class TimeConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(TimeConfig.class, ChatClock.class);

    @Test
    @DisplayName("the clock wires, and defaults to the Vietnam business calendar")
    void wiresWithTheDefaultZone() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(ChatClock.class);
            assertThat(context.getBean(ChatClock.class).zone())
                    .isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        });
    }

    @Test
    @DisplayName("a company outside Vietnam can move the calendar by configuration alone")
    void zoneIsConfigurable() {
        runner.withPropertyValues("app.business-zone=Europe/Paris").run(context ->
                assertThat(context.getBean(ChatClock.class).zone())
                        .isEqualTo(ZoneId.of("Europe/Paris")));
    }

    /**
     * The bean supplies an instant, not a calendar. If it carried the platform zone instead, code
     * that forgot to state a zone would silently inherit the container's — which is how "today"
     * came to answer for the wrong day in the first place.
     */
    @Test
    @DisplayName("the supplied clock is zone-neutral (UTC), so no caller inherits a zone by accident")
    void clockIsZoneNeutral() {
        runner.run(context ->
                assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Z")));
    }
}
