package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.api.dto.response.RoomRequestResponse;
import com.novax.leadora.application.usecase.quotation.QuotationAccessPolicy;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetRoomRequestsUseCase {

    private final RoomRequestRepository roomRequestRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;

    /**
     * The Reservation inbox, ordered by urgency: unanswered first, then soonest check-in, then
     * longest-waiting. See {@link RoomRequestRepository#findInbox} for why each key is there.
     *
     * <p>Unsorted {@code Pageable} — the ordering spans the status enum and lives in the query.
     */
    @Transactional(readOnly = true)
    public Page<RoomRequestResponse> execute(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        RoomRequestStatus parsed = null;
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            try {
                parsed = RoomRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_ROOM_REQUEST_STATUS",
                        "Unknown room request status: " + status, HttpStatus.BAD_REQUEST);
            }
        }
        return roomRequestRepository.findInbox(parsed, pageable).map(RoomRequestResponse::from);
    }

    /**
     * Every request raised for a quotation, newest first — drives the Sales-side panel
     * (current answer + why an earlier one no longer applies).
     */
    @Transactional(readOnly = true)
    public List<RoomRequestResponse> executeByQuotation(UUID quotationId) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));
        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        return roomRequestRepository.findByQuotation_QuotationIdOrderByCreatedAtDesc(quotationId)
                .stream()
                .map(RoomRequestResponse::from)
                .toList();
    }
}
