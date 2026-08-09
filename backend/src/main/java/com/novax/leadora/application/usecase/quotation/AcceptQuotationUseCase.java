package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.AcceptQuotationRequest;
import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.application.usecase.quotation.event.QuotationAcceptedEvent;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationAcceptanceLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationAcceptanceLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final ConvertToBookingUseCase convertToBookingUseCase;
    private final QuotationAcceptanceLogRepository acceptanceLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void execute(String token, AcceptQuotationRequest request) {
        execute(token, request, null, null);
    }

    @Transactional
    public void execute(String token, AcceptQuotationRequest request, String ipAddress, String userAgent) {
        QuotationEntity quotation = quotationRepository.findByAcceptanceToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation token", token));

        if (Boolean.TRUE.equals(quotation.getTokenUsed())) {
            throw new BusinessException("TOKEN_ALREADY_USED",
                    "This quotation link has already been used.",
                    HttpStatus.CONFLICT);
        }

        if (quotation.getTokenExpiry() != null && quotation.getTokenExpiry().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("TOKEN_EXPIRED",
                    "This quotation link has expired.",
                    HttpStatus.GONE);
        }

        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new BusinessException("QUOTATION_INVALID_STATUS",
                    "Only sent quotations can be accepted.",
                    HttpStatus.CONFLICT);
        }

        // Perform atomic check-and-set
        int updated = quotationRepository.consumeToken(token, OffsetDateTime.now());
        if (updated == 0) {
            throw new BusinessException("TOKEN_ALREADY_USED",
                    "This quotation link was already accepted or expired.",
                    HttpStatus.CONFLICT);
        }

        // Set status and tokenUsed
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setTokenUsed(true);
        QuotationEntity savedQuotation = quotationRepository.save(quotation);

        // Convert to booking, bypassing internal view policy checks
        ConvertToBookingRequest bookingRequest = ConvertToBookingRequest.builder()
                .specialRequests(request.getNotes())
                .build();
        convertToBookingUseCase.execute(savedQuotation.getQuotationId(), bookingRequest, true);

        // Log the customer response
        QuotationAcceptanceLogEntity logEntity = QuotationAcceptanceLogEntity.builder()
                .quotation(savedQuotation)
                .action("ACCEPTED")
                .loggedAt(OffsetDateTime.now())
                .customerNote(request.getNotes())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        acceptanceLogRepository.save(logEntity);

        // Publish event to decouple notification side-effects
        eventPublisher.publishEvent(new QuotationAcceptedEvent(this, savedQuotation));

        log.info("Quotation {} accepted successfully by token.", savedQuotation.getQuotationId());
    }
}
