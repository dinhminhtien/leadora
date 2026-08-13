package com.novax.leadora.infrastructure.integration.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.novax.leadora.api.dto.response.AbsaResponseDto;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

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

    public record AbsaRawResponseDto(
        String attitude,
        String speed,
        String accuracy,
        String facility,
        String price
    ) {}

    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public AbsaResponseDto analyze(String comment) {
        log.info("Sending ABSA analysis request for comment: {}", comment);

        try {
            String targetUrl = engineUrl.endsWith("/") ? engineUrl + "predict" : engineUrl + "/predict";
            Map<String, String> payload = Map.of("text", comment);

            AbsaRawResponseDto rawResponse = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(AbsaRawResponseDto.class);

            if (rawResponse == null) {
                throw new RuntimeException("Received null response from ABSA REST API");
            }

            SentimentResult att = parseSentimentString(rawResponse.attitude());
            SentimentResult spd = parseSentimentString(rawResponse.speed());
            SentimentResult acc = parseSentimentString(rawResponse.accuracy());
            SentimentResult fac = parseSentimentString(rawResponse.facility());
            SentimentResult prc = parseSentimentString(rawResponse.price());

            AbsaResponseDto response = new AbsaResponseDto(
                new com.novax.leadora.api.dto.response.AspectResultDto(att.getSentiment(), att.getConfidence()),
                new com.novax.leadora.api.dto.response.AspectResultDto(spd.getSentiment(), spd.getConfidence()),
                new com.novax.leadora.api.dto.response.AspectResultDto(acc.getSentiment(), acc.getConfidence()),
                new com.novax.leadora.api.dto.response.AspectResultDto(fac.getSentiment(), fac.getConfidence()),
                new com.novax.leadora.api.dto.response.AspectResultDto(prc.getSentiment(), prc.getConfidence())
            );

            log.info("Successfully received and parsed response from ABSA API: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Error occurred while interacting with ABSA AI Engine (attempt retry if applicable): {}", e.getMessage(), e);
            throw new RuntimeException("ABSA integration error: " + e.getMessage(), e);
        }
    }
}
