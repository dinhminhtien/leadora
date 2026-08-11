package com.novax.leadora.infrastructure.integration.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AbsaEngineClient {

    private final RestClient restClient;
    private final String engineUrl;

    private static final Pattern ABSA_PATTERN = Pattern.compile("([A-Za-z]+)\\s*\\((\\d+(?:\\.\\d+)?)%\\)");

    public AbsaEngineClient(
            @Value("${absa.engine.url}") String engineUrl,
            @Value("${absa.engine.connect-timeout:5000}") int connectTimeout,
            @Value("${absa.engine.read-timeout:5000}") int readTimeout) {
        this.engineUrl = engineUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Getter
    public static class SentimentResult {
        private final String sentiment;
        private final BigDecimal confidence;

        public SentimentResult(String sentiment, BigDecimal confidence) {
            this.sentiment = sentiment;
            this.confidence = confidence;
        }
    }

    public static SentimentResult parseSentimentString(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new SentimentResult(null, null);
        }
        Matcher matcher = ABSA_PATTERN.matcher(rawValue.trim());
        if (matcher.find()) {
            String sentiment = matcher.group(1);
            BigDecimal confidence = new BigDecimal(matcher.group(2));
            return new SentimentResult(sentiment, confidence);
        }
        return new SentimentResult(null, null);
    }

    public Map<String, SentimentResult> analyze(String comment) {
        log.info("Sending ABSA analysis request for comment: {}", comment);

        try {
            String targetUrl = engineUrl.endsWith("/") ? engineUrl + "predict" : engineUrl + "/predict";
            Map<String, String> payload = Map.of("text", comment);

            Map<String, String> response = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, String>>() {});

            if (response == null) {
                throw new RuntimeException("Received null response from ABSA REST API");
            }

            log.info("Successfully received response from ABSA API: {}", response);

            return Map.of(
                    "attitude", parseSentimentString(response.get("attitude")),
                    "speed", parseSentimentString(response.get("speed")),
                    "accuracy", parseSentimentString(response.get("accuracy")),
                    "facility", parseSentimentString(response.get("facility")),
                    "price", parseSentimentString(response.get("price"))
            );

        } catch (Exception e) {
            log.error("Error occurred while interacting with ABSA AI Engine: {}", e.getMessage(), e);
            throw new RuntimeException("ABSA integration error: " + e.getMessage(), e);
        }
    }
}
