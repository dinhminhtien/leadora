package com.novax.leadora.integration.ai;

import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.api.dto.response.AbsaResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
        AbsaResponseDto results = absaEngineClient.analyze(comment);

        assertThat(results).isNotNull();
        assertThat(results.attitude()).isNotNull();
        assertThat(results.attitude().sentiment()).isNotNull();
        assertThat(results.attitude().confidence()).isNotNull();

        assertThat(results.price()).isNotNull();
        assertThat(results.price().sentiment()).isNotNull();
        assertThat(results.price().confidence()).isNotNull();
    }
}
