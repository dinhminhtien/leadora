package com.novax.leadora.infrastructure.integration.email;

import com.novax.leadora.application.usecase.email.*;
import com.novax.leadora.application.usecase.email.exception.*;
import com.novax.leadora.config.ResendProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

@Component
@Slf4j
public class ResendEmailSender implements EmailGateway {

    private final RestClient restClient;
    private final ResendProperties properties;
    private final MeterRegistry meterRegistry;

    public ResendEmailSender(
            @Qualifier("resendRestClient") RestClient restClient,
            ResendProperties properties,
            MeterRegistry meterRegistry) {
        this.restClient = restClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Retryable(
        retryFor = {EmailRetryableException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public EmailSendResult send(EmailRequest request) {
        String sender = (request.from() != null && !request.from().isBlank()) ? request.from() : properties.defaultFrom();

        List<AttachmentPayload> attachmentPayloads = request.attachments().stream()
                .map(att -> new AttachmentPayload(
                        att.filename(),
                        encodeStreamToBase64(att.streamSupplier())
                )).toList();

        ResendPayload payload = new ResendPayload(
                sender,
                request.to(),
                request.cc(),
                request.bcc(),
                request.subject(),
                request.html(),
                attachmentPayloads.isEmpty() ? null : attachmentPayloads
        );

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri("/emails");

            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                requestSpec.header("Idempotency-Key", request.idempotencyKey());
            }

            ResendResponse response = requestSpec
                    .body(payload)
                    .retrieve()
                    .body(ResendResponse.class);

            sample.stop(meterRegistry.timer("email.send.duration", "status", "success"));
            meterRegistry.counter("email.send.success").increment();

            if (response != null && response.id() != null) {
                log.info("Email sent successfully. Provider: Resend | Message ID: {} | Subject: '{}' | Recipients: {}",
                        response.id(), request.subject(), request.to());
                return new EmailSendResult(response.id(), Instant.now(), true);
            } else {
                throw new EmailDeliveryException("Resend API returned empty response body");
            }

        } catch (RestClientResponseException e) {
            sample.stop(meterRegistry.timer("email.send.duration", "status", "failure"));
            meterRegistry.counter("email.send.failure", "reason", String.valueOf(e.getStatusCode().value())).increment();
            
            HttpStatusCode status = e.getStatusCode();
            log.error("Resend API Error. Subject: '{}' | Recipients: {} | HTTP Status: {}",
                    request.subject(), request.to(), status.value());

            if (status.value() == 429 || status.is5xxServerError()) {
                throw new EmailRetryableException("Retryable error from Resend: " + status.value(), e);
            } else {
                throw new EmailDeliveryException("Non-retryable error from Resend: " + status.value(), e);
            }
        } catch (ResourceAccessException e) {
            sample.stop(meterRegistry.timer("email.send.duration", "status", "failure"));
            meterRegistry.counter("email.send.failure", "reason", "timeout").increment();
            log.error("Resend Timeout/Network Error. Subject: '{}' | Error: {}", request.subject(), e.getMessage());
            throw new EmailRetryableException("Network/Timeout error during Resend communication", e);
        } catch (EmailRetryableException e) {
            throw e;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("email.send.duration", "status", "failure"));
            meterRegistry.counter("email.send.failure", "reason", "unexpected").increment();
            throw new EmailDeliveryException("Unexpected failure when sending email via Resend", e);
        }
    }

    private String encodeStreamToBase64(Supplier<InputStream> streamSupplier) {
        try (InputStream is = streamSupplier.get()) {
            byte[] buffer = is.readAllBytes();
            return Base64.getEncoder().encodeToString(buffer);
        } catch (Exception e) {
            throw new EmailDeliveryException("Failed to read stream for email attachment", e);
        }
    }

    private record ResendPayload(
        String from,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String html,
        List<AttachmentPayload> attachments
    ) {}

    private record AttachmentPayload(
        String filename,
        String content
    ) {}

    private record ResendResponse(
        String id
    ) {}
}
