package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationSendLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SendQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationSendLogRepository sendLogRepository;

    @Transactional
    public QuotationResponse execute(UUID quotationId, SendQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + quotationId));

        // BR-21: Only APPROVED quotations can be sent to customer
        if (quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED quotations can be sent. Current status: " + quotation.getStatus().name());
        }

        // POST-1: Update status to SENT
        quotation.setStatus(QuotationStatus.SENT);
        quotation.setSentAt(OffsetDateTime.now());
        QuotationEntity saved = quotationRepository.save(quotation);

        // POST-2: Record send log (BR-37: actor, action, timestamp, recipient details)
        QuotationSendLogEntity sendLog = QuotationSendLogEntity.builder()
                .quotation(saved)
                .version(saved.getVersion() != null ? saved.getVersion() : 1)
                .sendMethod(request.getSendMethod())
                .recipientName(request.getRecipientName())
                .recipientEmail(request.getRecipientEmail())
                .recipientPhone(request.getRecipientPhone())
                .sentByName(request.getSentByName())
                .sentByRole(request.getSentByRole())
                .personalMessage(request.getPersonalMessage())
                .build();
        sendLogRepository.save(sendLog);

        return QuotationResponse.from(saved);
    }
}
