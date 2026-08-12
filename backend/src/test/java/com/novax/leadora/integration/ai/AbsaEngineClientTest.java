package com.novax.leadora.integration.ai;

import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient.SentimentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.google.genai.vertex-ai=false",
        "spring.ai.google.genai.api-key=dummy-api-key",
        "spring.ai.google.genai.embedding.api-key=dummy-api-key",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "absa.engine.read-timeout=20000"
})
@ActiveProfiles("dev")
class AbsaEngineClientTest {

    @Autowired
    private AbsaEngineClient absaEngineClient;

    @Test
    void testAbsaIntegration() {
        String comment = "Very good service, but price is high and attitude was bad.";
        Map<String, SentimentResult> results = absaEngineClient.analyze(comment);

        assertThat(results).isNotNull();
        assertThat(results.keySet()).containsExactlyInAnyOrder("attitude", "speed", "accuracy", "facility", "price");

        // Verify attitude result
        SentimentResult attitude = results.get("attitude");
        assertThat(attitude.getSentiment()).isNotNull();
        assertThat(attitude.getConfidence()).isNotNull();

        // Verify price result
        SentimentResult price = results.get("price");
        assertThat(price.getSentiment()).isNotNull();
        assertThat(price.getConfidence()).isNotNull();
    }
}
