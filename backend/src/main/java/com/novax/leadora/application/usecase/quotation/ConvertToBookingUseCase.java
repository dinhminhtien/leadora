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
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
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
        private final StartSlaTrackingUseCase startSlaTrackingUseCase;
        private final com.novax.leadora.infrastructure.persistence.repository.ContractRepository contractRepository;
        private final com.novax.leadora.application.usecase.contract.ActivateContractUseCase activateContractUseCase;
        private final DealWorkflowSyncService dealWorkflowSyncService;

        @Transactional
        public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request) {
                QuotationEntity quotation = quotationRepository.findById(quotationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

                quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

                // Every rejection below names the unmet condition, the current state, and the next
                // step to clear it. A conversion is refused for a reason the user can act on — a
                // bare "400 Bad Request" leaves them guessing which part of the workflow is missing.

                // PRE-1: Only ACCEPTED, ACCEPTED_BY_CUSTOMER, or RESERVATION_PENDING quotations can be converted
                // Note: For portal/OTP acceptance, the status is RESERVATION_PENDING.
                // This use case should only be invoked for RESERVATION_PENDING by ApproveReservationUseCase
                // after the Reservation staff has confirmed availability.
                if (quotation.getStatus() != QuotationStatus.ACCEPTED
                        && quotation.getStatus() != QuotationStatus.ACCEPTED_BY_CUSTOMER
                        && quotation.getStatus() != QuotationStatus.RESERVATION_PENDING) {
                        throw new BusinessException("QUOTATION_INVALID_STATUS",
                                        "This quotation is " + quotation.getStatus().name()
                                                        + ", and a booking can only be created once the customer has accepted it. "
                                                        + "Two routes get there: the customer accepts through the quotation portal and verifies the OTP "
                                                        + "(status becomes RESERVATION_PENDING), or Sales records the customer's acceptance on the "
                                                        + "quotation (status becomes ACCEPTED). Complete one of those first, then convert.",
                                        HttpStatus.CONFLICT);
                }

                boolean isAcceptedByCustomer = quotation.getStatus() == QuotationStatus.ACCEPTED_BY_CUSTOMER
                        || quotation.getStatus() == QuotationStatus.RESERVATION_PENDING;

                // Check contract status
                List<ContractEntity> contracts =
                        contractRepository.findByQuotation_QuotationId(quotationId);

                if (contracts.isEmpty()) {
                        throw new BusinessException("CONTRACT_REQUIRED",
                                        "This quotation has no contract yet, and a booking is always created from a contract. "
                                                        + "Generate the contract for this quotation, send it to the customer, and have them confirm it "
                                                        + "with the OTP — then convert.",
                                        HttpStatus.BAD_REQUEST);
                }

                // Get the latest version contract
                ContractEntity contract = contracts.stream()
                        .max(java.util.Comparator.comparingInt(c->c.getVersion()))
                        .get();

                String contractLabel = contract.getContractCode() != null
                                ? "Contract " + contract.getContractCode()
                                : "The contract for this quotation";

                if (isAcceptedByCustomer) {
                        // The customer already proved their intent with the portal OTP, so a contract that
                        // is merely drafted or sent is enough — it does not have to be signed back yet.
                        if (contract.getStatus() != ContractStatus.DRAFT
                                        && contract.getStatus() != ContractStatus.SENT
                                        && contract.getStatus() != ContractStatus.ACKNOWLEDGED
                                        && contract.getStatus() != ContractStatus.ACTIVE) {
                                throw new BusinessException("CONTRACT_INVALID_STATE",
                                                contractLabel + " is " + contract.getStatus()
                                                                + ", so it can no longer be turned into a booking. Only a contract that is still live "
                                                                + "(DRAFT, SENT, ACKNOWLEDGED or ACTIVE) can be converted — a "
                                                                + contract.getStatus() + " contract is closed. "
                                                                + "Generate a new contract for this quotation and send it to the customer.",
                                                HttpStatus.BAD_REQUEST);
                        }
                } else {
                        // Acceptance was recorded by Sales, not by the customer. The customer's own
                        // acknowledgement on the contract is what authorises the booking here.
                        if (contract.getStatus() == ContractStatus.DRAFT
                                        || contract.getStatus() == ContractStatus.SENT) {
                                String nextStep = contract.getStatus() == ContractStatus.DRAFT
                                                ? "Send " + (contract.getContractCode() != null ? contract.getContractCode() : "the contract")
                                                                + " to the customer, then ask them to open the link in that email and confirm with the OTP."
                                                : "It has already been sent — ask the customer to open the link in that email and confirm with the OTP.";
                                throw new BusinessException("CONTRACT_NOT_ACKNOWLEDGED",
                                                "This quotation's acceptance was recorded by Sales rather than by the customer, so the customer "
                                                                + "must acknowledge the contract before a booking can be created. " + contractLabel
                                                                + " is currently " + contract.getStatus()
                                                                + " and has to reach ACKNOWLEDGED. " + nextStep,
                                                HttpStatus.BAD_REQUEST);
                        }

                        if (contract.getStatus() != ContractStatus.ACKNOWLEDGED
                                        && contract.getStatus() != ContractStatus.ACTIVE) {
                                throw new BusinessException("CONTRACT_INVALID_STATE",
                                                contractLabel + " is " + contract.getStatus()
                                                                + ", so it can no longer be turned into a booking. Conversion needs a contract the customer "
                                                                + "has acknowledged (ACKNOWLEDGED or ACTIVE). Generate a new contract for this quotation, "
                                                                + "send it, and have the customer confirm it with the OTP.",
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
                                        "A booking needs both a check-in and a check-out date (BR-23). This quotation does not "
                                                        + "carry "
                                                        + (checkInDate == null && checkOutDate == null ? "either date"
                                                                        : checkInDate == null ? "a check-in date" : "a check-out date")
                                                        + ", so supply the missing date(s) in the conversion form, or revise the quotation to include them.",
                                        HttpStatus.BAD_REQUEST);
                }
                if (!checkOutDate.isAfter(checkInDate)) {
                        throw new BusinessException("INVALID_DATES",
                                        "The stay dates are the wrong way round: check-out (" + checkOutDate
                                                        + ") must fall after check-in (" + checkInDate
                                                        + "). Correct the dates and convert again.",
                                        HttpStatus.BAD_REQUEST);
                }

                // BR-23: Customer identity required
                if (quotation.getCustomer() == null) {
                        throw new BusinessException("CUSTOMER_MISSING",
                                        "A booking has to be held in a customer's name, and this quotation has no customer linked to "
                                                        + "it (BR-23). Link a customer to the deal behind this quotation, then convert.",
                                        HttpStatus.UNPROCESSABLE_ENTITY);
                }

                // Room confirmation is not required to convert. The booking is created PENDING
                // and
                // the Reservation team confirms it separately, so an unconfirmed room delays t
                // e
                // booking rather than blocking the conversion.
                //

                // Line items may span several room types (BR-23) — fetch them once, up front,
                // both for the availability re-check below and to copy into booking details.
                List<QuotationDetailEntity> quotationDetails = quotationDetailRepository
                                .findByQuotation_QuotationId(quotationId);

                // E3: every room type must still be available for the (possibly
                // re-confirmed) dates — BR-24
                List<String> roomTypes = quotationDetails.stream()
                                .map(d -> d.getDescription())
                                .toList();
                availabilityChecker.assertRoomsAvailable(checkInDate, checkOutDate, roomTypes);

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
