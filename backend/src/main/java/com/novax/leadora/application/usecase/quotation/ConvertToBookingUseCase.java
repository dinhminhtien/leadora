package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.inventory.RoomAllotmentHoldService;
import com.novax.leadora.application.usecase.inventory.RoomLineDemand;
import com.novax.leadora.application.usecase.roomrequest.RoomConfirmationReader;
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
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
        private final QuotationAvailabilityChecker availabilityChecker;
        private final RoomAllotmentHoldService roomAllotmentHoldService;
        private final RoomConfirmationReader roomConfirmationReader;
        private final StartSlaTrackingUseCase startSlaTrackingUseCase;
        private final com.novax.leadora.infrastructure.persistence.repository.ContractRepository contractRepository;
        private final com.novax.leadora.application.usecase.contract.ActivateContractUseCase activateContractUseCase;
        private final DealWorkflowSyncService dealWorkflowSyncService;

        @Transactional
        public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request) {
                // Locked read, and the first lock taken in this transaction. Two clicks on
                // Convert used to be able to run side by side: both would pass the status check
                // and both create a booking, whose code is derived from the quotation id and is
                // therefore identical — so the second failed on a unique-constraint violation
                // rather than a business message. It also anchors the lock order the rest of the
                // flow follows: quotation, then booking, then allotment nights.
                QuotationEntity quotation = quotationRepository.findByIdForUpdate(quotationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

                quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

                // PRE-1: Only ACCEPTED, ACCEPTED_BY_CUSTOMER, or RESERVATION_PENDING quotations can be converted
                // Note: For portal/OTP acceptance, the status is RESERVATION_PENDING.
                // This use case should only be invoked for RESERVATION_PENDING by ApproveReservationUseCase
                // after the Reservation staff has confirmed availability.
                if (quotation.getStatus() != QuotationStatus.ACCEPTED 
                        && quotation.getStatus() != QuotationStatus.ACCEPTED_BY_CUSTOMER
                        && quotation.getStatus() != QuotationStatus.RESERVATION_PENDING) {
                        throw new BusinessException("QUOTATION_INVALID_STATUS",
                                        "Only ACCEPTED, ACCEPTED_BY_CUSTOMER, or RESERVATION_PENDING quotations can be converted to a booking. Current status: "
                                                         + quotation.getStatus().name(),
                                        HttpStatus.CONFLICT);
                }

                boolean isAcceptedByCustomer = quotation.getStatus() == QuotationStatus.ACCEPTED_BY_CUSTOMER 
                        || quotation.getStatus() == QuotationStatus.RESERVATION_PENDING;
                
                // Check contract status
                List<ContractEntity> contracts = 
                        contractRepository.findByQuotation_QuotationId(quotationId);
                
                if (contracts.isEmpty()) {
                        throw new BusinessException("CONTRACT_REQUIRED",
                                        "A contract must be generated and signed before converting to a booking.",
                                        HttpStatus.BAD_REQUEST);
                }
                
                // Get the latest version contract
                ContractEntity contract = contracts.stream()
                        .max(java.util.Comparator.comparingInt(c->c.getVersion()))
                        .get();

                if (isAcceptedByCustomer) {
                        if (contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.DRAFT &&
                            contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.SENT &&
                            contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.ACKNOWLEDGED &&
                            contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.ACTIVE) {
                                throw new BusinessException("CONTRACT_INVALID_STATE",
                                                "The contract is in an invalid state for booking: " + contract.getStatus(),
                                                HttpStatus.BAD_REQUEST);
                        }
                } else {
                        if (contract.getStatus() == com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.DRAFT ||
                            contract.getStatus() == com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.SENT) {
                                throw new BusinessException("CONTRACT_NOT_ACKNOWLEDGED",
                                                "The contract has not been acknowledged by the customer. Please verify OTP first.",
                                                HttpStatus.BAD_REQUEST);
                        }

                        if (contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.ACKNOWLEDGED &&
                            contract.getStatus() != com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.ACTIVE) {
                                throw new BusinessException("CONTRACT_INVALID_STATE",
                                                "The contract is in an invalid state for booking: " + contract.getStatus(),
                                                HttpStatus.BAD_REQUEST);
                        }
                }

                // BR-23: Resolve dates — request values take precedence, fall back to quotation
                LocalDate checkInDate = request.getCheckInDate() != null ? request.getCheckInDate()
                                : quotation.getCheckInDate();
                LocalDate checkOutDate = request.getCheckOutDate() != null ? request.getCheckOutDate()
                                : quotation.getCheckOutDate();

                if (checkInDate == null || checkOutDate == null) {
                        throw new BusinessException("INVALID_DATES",
                                        "Check-in and check-out dates are required for booking conversion (BR-23)",
                                        HttpStatus.BAD_REQUEST);
                }
                if (!checkOutDate.isAfter(checkInDate)) {
                        throw new BusinessException("INVALID_DATES", "Check-out date must be after check-in date",
                                        HttpStatus.BAD_REQUEST);
                }

                // BR-23: Customer identity required
                if (quotation.getCustomer() == null) {
                        throw new BusinessException("CUSTOMER_MISSING", "Customer information is missing (BR-23)",
                                        HttpStatus.UNPROCESSABLE_ENTITY);
                }

                // Line items may span several room types (BR-23) — fetch them once, up front,
                // both for the availability re-check below and to copy into booking details.
                List<QuotationDetailEntity> quotationDetails = quotationDetailRepository
                                .findByQuotation_QuotationId(quotationId);

                // E3/BR-24: this is where the CRM stops offering and starts committing, so it is
                // the one point in the quotation flow that refuses to proceed on unconfirmed
                // rooms. Creating and revising a quotation deliberately do not — an offer costs
                // nothing if the hotel turns out to be full, whereas a booking promises a guest a
                // room. A confirmed room request satisfies the check on its own: the Reservation
                // team has looked at the real PMS, which outranks our allotment figures.
                //
                // Demands are resolved by product_id. This used to re-match the free-text
                // description against product names, which meant a line whose description had
                // been edited could never be found.
                // Refuse if *any* line is unlinked, not merely if they all are. Skipping the
                // unlinked ones and checking the rest would be the worst of both: the booking is
                // still created from every line, so those rooms get committed to a guest without
                // ever being checked against allotment or held — a partial oversell, produced by
                // the very guard whose message promises it cannot happen.
                if (quotationDetails.stream().anyMatch(d -> d.getProductService() == null)) {
                        throw new BusinessException("QUOTATION_LINES_UNRESOLVED",
                                        "One or more of this quotation's room lines are not linked to a room type,"
                                                        + " so availability cannot be verified. Revise the quotation and"
                                                        + " re-select the room types before converting.",
                                        HttpStatus.CONFLICT);
                }

                List<RoomLineDemand> demands = quotationDetails.stream()
                                .map(d -> new RoomLineDemand(d.getProductService().getProductId(), d.getQuantity()))
                                .toList();

                if (demands.isEmpty()) {
                        throw new BusinessException("QUOTATION_LINES_UNRESOLVED",
                                        "This quotation has no room lines to convert.",
                                        HttpStatus.CONFLICT);
                }

                boolean roomsConfirmed = roomConfirmationReader.isRoomConfirmed(quotation);
                availabilityChecker.assertCanCommit(checkInDate, checkOutDate, demands,
                                quotationId, roomsConfirmed);

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

                // Copy quotation line items into booking details, holding inventory for each
                // room/service
                List<BookingDetailEntity> bookingDetails = quotationDetails.stream()
                                .map(detail -> BookingDetailEntity.builder()
                                                .booking(saved)
                                                .productService(detail.getProductService())
                                                .description(detail.getDescription())
                                                .quantity(detail.getQuantity())
                                                .unitPrice(detail.getUnitPrice())
                                                .nights(detail.getNights())
                                                .lineTotal(detail.getLineTotal())
                                                .inventoryStatus(InventoryStatus.ALLOCATED)
                                                .build())
                                .toList();

                bookingDetailRepository.saveAll(bookingDetails);

                // Hand the deduction over from the quotation's hold to the booking, in this same
                // transaction (BR-47). The booking now counts against allotment, so leaving the
                // hold active would take the rooms twice; releasing it in a later transaction
                // would risk the booking rolling back after the rooms had already been given
                // back, and the last room being quietly resold.
                roomAllotmentHoldService.convertForQuotation(quotationId, saved);

                // POST-1: Update quotation status to CONVERTED or BOOKING_REQUEST
                if (isAcceptedByCustomer) {
                        quotation.setStatus(QuotationStatus.BOOKING_REQUEST);
                } else {
                        quotation.setStatus(QuotationStatus.CONVERTED);
                }
                quotationRepository.save(quotation);

                // Activate the contract if it is ACKNOWLEDGED
                if (contract != null && contract.getStatus() == com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus.ACKNOWLEDGED) {
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
