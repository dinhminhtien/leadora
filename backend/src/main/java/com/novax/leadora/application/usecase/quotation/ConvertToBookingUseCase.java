package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertToBookingUseCase {

        private final QuotationRepository quotationRepository;
        private final BookingRepository bookingRepository;
        private final QuotationDetailRepository quotationDetailRepository;
        private final BookingDetailRepository bookingDetailRepository;
        private final QuotationAccessPolicy quotationAccessPolicy;
        private final QuotationActionPolicy quotationActionPolicy;
        private final StartSlaTrackingUseCase startSlaTrackingUseCase;
        private final com.novax.leadora.application.usecase.contract.ActivateContractUseCase activateContractUseCase;
        private final DealWorkflowSyncService dealWorkflowSyncService;

        @Transactional
        public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request) {
                // Locked read, and the first lock taken in this transaction. Two clicks on
                // Convert used to be able to run side by side: both would pass the status check
                // and both create a booking, whose code is derived from the quotation id and is
                // therefore identical — so the second failed on a unique-constraint violation
                // rather than a business message. It also anchors the lock order the rest of the
                // flow follows: quotation, then booking.
                QuotationEntity quotation = quotationRepository.findByIdForUpdate(quotationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

                quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

                // BR-23: Resolve dates — request values take precedence, fall back to quotation.
                // Resolved before the gate so the preconditions are judged against the dates the
                // booking will actually carry.
                LocalDate checkInDate = request.getCheckInDate() != null ? request.getCheckInDate()
                                : quotation.getCheckInDate();
                LocalDate checkOutDate = request.getCheckOutDate() != null ? request.getCheckOutDate()
                                : quotation.getCheckOutDate();

                // Status, prior booking, customer, dates, contract and room lines — every
                // precondition in one place, shared with the eligibility endpoint the UI reads, so
                // a button is never offered for a conversion this would refuse. Each refusal names
                // the unmet condition, the current state, and the next step to clear it.
                ContractEntity contract =
                                quotationActionPolicy.assertConvertible(quotation, checkInDate, checkOutDate);

                boolean isAcceptedByCustomer = quotation.getStatus() == QuotationStatus.ACCEPTED_BY_CUSTOMER
                        || quotation.getStatus() == QuotationStatus.RESERVATION_PENDING;

                // Line items may span several room types (BR-23). The policy has already refused
                // any line that is not linked to a room type, so every line here can be carried
                // onto the booking request the Reservation team will read.
                List<QuotationDetailEntity> quotationDetails = quotationDetailRepository
                                .findByQuotation_QuotationId(quotationId);

                // The room condition was checked by the policy above, and it has exactly one
                // authority: the Reservation team's recorded answer. This used to consult
                // Leadora's own allotment arithmetic and refuse when its figures came up short —
                // which is this system determining availability, the thing Report 1 (FE-19,
                // LI-02) reserves for Reservation.

                // Generate booking code from year + quotation UUID prefix (unique per
                // quotation)
                String bookingCode = "BK-" + checkInDate.getYear() + "-"
                //
                                + quotationId.toString().substring(0, 8).toUpperCase();

                // POST-2: Create pending booking record
                BookingEntity booking = BookingEntity.builder()
                                .quotation(quotation)
                                .customer(quotation.getCustomer())
                                .assignedUser(quotation.getCreatedBy())
                                .bookingCode(bookingCode)
                                .checkInDate(checkInDate)
                                .checkOutDate(checkOutDate)
                                .status(BookingStatus.PENDING)
                                .specialRequests(request.getSpecialRequests())
                                .totalAmount(quotation.getTotalAmount())
                                .build();

                BookingEntity saved = bookingRepository.save(booking);

                // Copy the quotation's line items onto the booking request. The rooms are marked
                // AVAILABLE, not ALLOCATED: allocation is the Reservation team's act, and it
                // happens when they confirm the request. Nothing is held or deducted here.
                List<BookingDetailEntity> bookingDetails = quotationDetails.stream()
                                .map(detail -> BookingDetailEntity.builder()
                                                .booking(saved)
                                                .productService(detail.getProductService())
                                                .description(detail.getDescription())
                                                .quantity(detail.getQuantity())
                                                .unitPrice(detail.getUnitPrice())
                                                .nights(detail.getNights())
                                                .lineTotal(detail.getLineTotal())
                                                .inventoryStatus(InventoryStatus.AVAILABLE)
                                                .build())
                                .toList();

                bookingDetailRepository.saveAll(bookingDetails);

                // POST-1: Update quotation status to CONVERTED or BOOKING_REQUEST
                if (isAcceptedByCustomer) {
                        quotation.setStatus(QuotationStatus.BOOKING_REQUEST);
                } else {
                        quotation.setStatus(QuotationStatus.CONVERTED);
                }
                quotationRepository.save(quotation);

                // Activate the contract if it is ACKNOWLEDGED
                if (contract.getStatus() == ContractStatus.ACKNOWLEDGED) {
                        activateContractUseCase.execute(contract.getId());
                }

                // UC-17.2: start SLA tracking — non-fatal if no BOOKING_CONFIRM rule configured
                try {
                        startSlaTrackingUseCase.execute("BOOKING_CONFIRM", "BOOKING", saved.getBookingId());
                } catch (Exception e) {
                        log.warn("SLA tracking failed for booking {}: {}", saved.getBookingId(), e.getMessage());
                }

                // Sync deal pipeline stage
                if (quotation.getDeal() != null) {
                        try {
                                dealWorkflowSyncService.syncPipelineStage(quotation.getDeal().getDealId());
                        } catch (Exception e) {
                                log.warn("Failed to sync deal stage for deal {}: {}", quotation.getDeal().getDealId(), e.getMessage());
                        }
                }

                return BookingResponse.from(saved);
        }
}
