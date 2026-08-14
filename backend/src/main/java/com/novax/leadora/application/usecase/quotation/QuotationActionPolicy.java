package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.application.usecase.email.EmailContactPolicy;
import com.novax.leadora.application.usecase.roomrequest.RoomConfirmationReader;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single answer to "may this quotation be sent / asked about / converted, and if not, why".
 *
 * <p>Both the write path and the read path go through here. {@code ConvertToBookingUseCase} calls
 * {@link #assertConvertible} before it changes anything, and the eligibility endpoint calls the
 * same methods to tell the client which buttons to offer. That is the point of the class: the
 * reason the UI shows and the reason the server enforces cannot drift apart, because there is
 * only one of them.
 *
 * <p>Previously the front end had its own copy of these rules and got them wrong in the way that
 * matters most — it offered "Convert to Booking" the moment a quotation reached ACCEPTED, while
 * the server also required a contract the customer had acknowledged. The button was live, the
 * request always failed, and nothing on screen said why.
 *
 * <p><b>Availability is read, never computed.</b> The room condition below is the Reservation
 * team's recorded answer plus the allotment they published. This CRM never decides for itself
 * that a room is free.
 */
@Component
@RequiredArgsConstructor
public class QuotationActionPolicy {

    /** BR-21 — the only status from which a quotation may go out to the customer. */
    private static final QuotationStatus SENDABLE = QuotationStatus.APPROVED;

    /**
     * Statuses a booking can be created from. {@code ACCEPTED} is Sales recording the customer's
     * answer; the other two are the customer accepting through the portal, before and after the
     * Reservation team has picked it up.
     */
    private static final List<QuotationStatus> CONVERTIBLE = List.of(
            QuotationStatus.ACCEPTED,
            QuotationStatus.ACCEPTED_BY_CUSTOMER,
            QuotationStatus.RESERVATION_PENDING);


    private final ContractRepository contractRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final BookingRepository bookingRepository;
    private final RoomConfirmationReader roomConfirmationReader;

    /**
     * Whether an action may be taken, and the refusal to show when it may not.
     *
     * <p>Carries the HTTP status and error code the server would answer with, so the client
     * renders the identical wording whether it asked in advance or found out by trying.
     */
    public record Verdict(boolean allowed, String errorCode, String message, String field, HttpStatus status) {

        public static Verdict allow() {
            return new Verdict(true, null, null, null, null);
        }

        public static Verdict block(String errorCode, String message, HttpStatus status) {
            return new Verdict(false, errorCode, message, null, status);
        }

        public static Verdict blockField(String errorCode, String message, HttpStatus status, String field) {
            return new Verdict(false, errorCode, message, field, status);
        }

        public void assertAllowed() {
            if (!allowed) {
                throw new BusinessException(errorCode, message, null, status, field);
            }
        }
    }

    // ── Send to customer (UC-14.4) ────────────────────────────────────────────────────────

    /** Status and customer identity — the part of sending that no delivery method escapes. */
    public Verdict canSend(QuotationEntity quotation) {
        if (quotation.getStatus() != SENDABLE) {
            return Verdict.block("QUOTATION_NOT_SENDABLE",
                    "This quotation is " + quotation.getStatus().name() + ". Only an "
                            + SENDABLE.name() + " quotation can be sent to the customer (BR-21)."
                            + (quotation.getStatus() == QuotationStatus.DRAFT
                                    ? " Submit it for approval first."
                                    : quotation.getStatus() == QuotationStatus.PENDING_APPROVAL
                                            ? " It is waiting on a manager's approval."
                                            : ""),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (quotation.getCustomer() == null) {
            return Verdict.block("RECIPIENT_NOT_FOUND",
                    "This quotation has no customer linked to it, so there is nobody to send it to. "
                            + "Link a customer to the deal behind this quotation, then send.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return Verdict.allow();
    }

    /** Sending by email additionally needs a deliverable address (E3). */
    public Verdict canSendByEmail(QuotationEntity quotation) {
        Verdict base = canSend(quotation);
        if (!base.allowed()) {
            return base;
        }
        String email = quotation.getCustomer().getEmail();
        if (email == null || email.isBlank()) {
            return Verdict.blockField("INVALID_CONTACT_INFORMATION",
                    "Cannot send by email: no email address is recorded for "
                            + customerLabel(quotation) + ". Add the customer's email address, "
                            + "or enter one for this send.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "customer.email");
        }
        if (!EmailContactPolicy.isValidEmail(email)) {
            return Verdict.blockField("INVALID_EMAIL_FORMAT",
                    "Cannot send by email: \"" + email.trim() + "\" is not a valid email address. "
                            + "Correct it on the customer record, or enter a valid address for this send.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "customer.email");
        }
        return Verdict.allow();
    }

    /** Sending by WhatsApp/SMS needs a number instead. */
    public Verdict canSendByPhone(QuotationEntity quotation) {
        Verdict base = canSend(quotation);
        if (!base.allowed()) {
            return base;
        }
        String phone = quotation.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            return Verdict.blockField("INVALID_CONTACT_INFORMATION",
                    "Cannot send by WhatsApp/SMS: no phone number is recorded for "
                            + customerLabel(quotation) + ". Add the customer's phone number, "
                            + "or enter one for this send.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "customer.phone");
        }
        return Verdict.allow();
    }

    // ── Convert to booking (UC-14.7) ──────────────────────────────────────────────────────

    /**
     * Every condition on creating a booking from this quotation, evaluated in the order the user
     * would have to satisfy them, using the quotation's own dates.
     */
    public Verdict canConvert(QuotationEntity quotation) {
        return convertibleWithoutRooms(quotation,
                quotation.getCheckInDate(), quotation.getCheckOutDate());
    }

    /**
     * The write path's gate. Runs the same checks as {@link #canConvert} except the room
     * condition, which the use case evaluates itself once it has loaded the line items it also
     * needs for the booking.
     *
     * <p>The dates are passed in rather than read off the quotation because the conversion form
     * may supply them: a quotation that never carried stay dates can still be converted if the
     * user fills them in, and it is those dates the booking is checked against.
     *
     * @return the contract the booking will be created from
     */
    public ContractEntity assertConvertible(QuotationEntity quotation, LocalDate checkIn, LocalDate checkOut) {
        convertibleWithoutRooms(quotation, checkIn, checkOut).assertAllowed();
        return latestContract(quotation.getQuotationId()).orElseThrow();
    }

    private Verdict convertibleWithoutRooms(QuotationEntity quotation, LocalDate checkIn, LocalDate checkOut) {
        if (!CONVERTIBLE.contains(quotation.getStatus())) {
            return Verdict.block("QUOTATION_INVALID_STATUS", statusRefusal(quotation), HttpStatus.CONFLICT);
        }
        if (!bookingRepository.findByQuotation_QuotationId(quotation.getQuotationId()).isEmpty()) {
            return Verdict.block("ALREADY_CONVERTED",
                    "A booking has already been created from this quotation. Open the booking to "
                            + "carry on from there.",
                    HttpStatus.CONFLICT);
        }
        if (quotation.getCustomer() == null) {
            return Verdict.block("CUSTOMER_MISSING",
                    "A booking has to be held in a customer's name, and this quotation has no customer "
                            + "linked to it (BR-23). Link a customer to the deal behind this quotation, then convert.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (checkIn == null || checkOut == null) {
            return Verdict.blockField("INVALID_DATES",
                    "A booking needs both a check-in and a check-out date (BR-23). This quotation carries "
                            + (checkIn == null && checkOut == null ? "neither date"
                                    : checkIn == null ? "no check-in date" : "no check-out date")
                            + ", so supply the missing date(s) in the conversion form, or revise the quotation "
                            + "to include them.",
                    HttpStatus.BAD_REQUEST,
                    checkIn == null ? "checkInDate" : "checkOutDate");
        }
        if (!checkOut.isAfter(checkIn)) {
            return Verdict.blockField("INVALID_DATES",
                    "The stay dates are the wrong way round: check-out (" + checkOut
                            + ") must fall after check-in (" + checkIn + "). Correct the dates and convert again.",
                    HttpStatus.BAD_REQUEST, "checkOutDate");
        }

        Optional<ContractEntity> latest = latestContract(quotation.getQuotationId());
        if (latest.isEmpty()) {
            return Verdict.block("CONTRACT_REQUIRED",
                    "This quotation has no contract yet, and a booking is always created from a contract. "
                            + "Generate the contract for this quotation, send it to the customer, and have them "
                            + "confirm it with the OTP — then convert.",
                    HttpStatus.BAD_REQUEST);
        }
        Verdict contractVerdict = contractReady(quotation, latest.get());
        if (!contractVerdict.allowed()) {
            return contractVerdict;
        }

        List<QuotationDetailEntity> lines =
                quotationDetailRepository.findByQuotation_QuotationId(quotation.getQuotationId());
        if (lines.isEmpty()) {
            return Verdict.block("QUOTATION_LINES_UNRESOLVED",
                    "This quotation has no room lines to convert.", HttpStatus.CONFLICT);
        }
        if (lines.stream().anyMatch(line -> line.getProductService() == null)) {
            return Verdict.block("QUOTATION_LINES_UNRESOLVED",
                    "One or more of this quotation's room lines are not linked to a room type, so "
                            + "the Reservation team cannot be told what to check. Revise the quotation and "
                            + "re-select the room types before converting.",
                    HttpStatus.CONFLICT);
        }

        return roomsConfirmedByReservation(quotation);
    }

    /**
     * The room gate, and the only one: has the Reservation team confirmed these rooms?
     *
     * <p>Report 1 (FE-19, LI-02) makes Reservation the single source of truth for availability, so
     * this reads their recorded answer and nothing else. It previously consulted Leadora's own
     * allotment arithmetic first and only fell back to their answer when the figures were short —
     * which let a booking be created on the strength of a number this system worked out for
     * itself, and is the "independently determine" the scope rules out.
     *
     * <p>{@code RoomConfirmationReader} also checks the answer still describes the quotation: a
     * confirmation given for other dates or another room type no longer applies, and a hold the
     * Reservation team put a deadline on has to still be live.
     */
    private Verdict roomsConfirmedByReservation(QuotationEntity quotation) {
        if (roomConfirmationReader.isRoomConfirmed(quotation)) {
            return Verdict.allow();
        }

        RoomRequestStatus status = roomConfirmationReader.currentRequest(quotation.getQuotationId())
                .map(r -> r.getStatus())
                .orElse(null);

        String detail;
        if (status == null) {
            detail = "The Reservation team has not been asked about these rooms yet. "
                    + "Request availability, and convert once they confirm.";
        } else if (status == RoomRequestStatus.PENDING) {
            detail = "The Reservation team is still checking. Convert once they have answered.";
        } else if (status == RoomRequestStatus.REJECTED) {
            detail = "The Reservation team could not confirm these rooms. Revise the quotation "
                    + "with dates or a room type they can meet, then ask again.";
        } else {
            // CONFIRMED, but the answer no longer describes what the quotation says, or its
            // hold deadline has passed.
            detail = "The rooms were confirmed for different dates or another room type, or the "
                    + "hold the Reservation team gave has lapsed. Request availability again for "
                    + "the current details.";
        }

        return Verdict.block("ROOM_NOT_CONFIRMED",
                "Cannot convert to booking: room availability has not been confirmed by Reservation. "
                        + detail,
                HttpStatus.CONFLICT);
    }

    private Verdict contractReady(QuotationEntity quotation, ContractEntity contract) {
        String label = contract.getContractCode() != null
                ? "Contract " + contract.getContractCode()
                : "The contract for this quotation";

        boolean acceptedByCustomer = quotation.getStatus() == QuotationStatus.ACCEPTED_BY_CUSTOMER
                || quotation.getStatus() == QuotationStatus.RESERVATION_PENDING;

        if (acceptedByCustomer) {
            // The customer already proved their intent with the portal OTP, so a contract that is
            // merely drafted or sent is enough — it does not have to be signed back yet.
            boolean live = contract.getStatus() == ContractStatus.DRAFT
                    || contract.getStatus() == ContractStatus.SENT
                    || contract.getStatus() == ContractStatus.ACKNOWLEDGED
                    || contract.getStatus() == ContractStatus.ACTIVE;
            if (!live) {
                return Verdict.block("CONTRACT_INVALID_STATE",
                        label + " is " + contract.getStatus() + ", so it can no longer be turned into a "
                                + "booking. Only a contract that is still live (DRAFT, SENT, ACKNOWLEDGED or "
                                + "ACTIVE) can be converted. Generate a new contract for this quotation and "
                                + "send it to the customer.",
                        HttpStatus.BAD_REQUEST);
            }
            return Verdict.allow();
        }

        // Acceptance was recorded by Sales, not by the customer. The customer's own
        // acknowledgement on the contract is what authorises the booking here.
        if (contract.getStatus() == ContractStatus.DRAFT || contract.getStatus() == ContractStatus.SENT) {
            String nextStep = contract.getStatus() == ContractStatus.DRAFT
                    ? "Send " + (contract.getContractCode() != null ? contract.getContractCode() : "the contract")
                            + " to the customer, then ask them to open the link in that email and confirm with the OTP."
                    : "It has already been sent — ask the customer to open the link in that email and confirm with the OTP.";
            return Verdict.block("CONTRACT_NOT_ACKNOWLEDGED",
                    "This quotation's acceptance was recorded by Sales rather than by the customer, so the "
                            + "customer must acknowledge the contract before a booking can be created. " + label
                            + " is currently " + contract.getStatus() + " and has to reach ACKNOWLEDGED. " + nextStep,
                    HttpStatus.BAD_REQUEST);
        }
        if (contract.getStatus() != ContractStatus.ACKNOWLEDGED && contract.getStatus() != ContractStatus.ACTIVE) {
            return Verdict.block("CONTRACT_INVALID_STATE",
                    label + " is " + contract.getStatus() + ", so it can no longer be turned into a booking. "
                            + "Conversion needs a contract the customer has acknowledged (ACKNOWLEDGED or ACTIVE). "
                            + "Generate a new contract for this quotation, send it, and have the customer confirm "
                            + "it with the OTP.",
                    HttpStatus.BAD_REQUEST);
        }
        return Verdict.allow();
    }

    private Optional<ContractEntity> latestContract(UUID quotationId) {
        return contractRepository.findByQuotation_QuotationId(quotationId).stream()
                .max(Comparator.comparingInt(c -> c.getVersion()));
    }

    private static String statusRefusal(QuotationEntity quotation) {
        return "This quotation is " + quotation.getStatus().name()
                + ", and a booking can only be created once the customer has accepted it. "
                + "Two routes get there: the customer accepts through the quotation portal and verifies the OTP "
                + "(status becomes RESERVATION_PENDING), or Sales records the customer's acceptance on the "
                + "quotation (status becomes ACCEPTED). Complete one of those first, then convert.";
    }

    private static String customerLabel(QuotationEntity quotation) {
        String name = quotation.getCustomer() != null ? quotation.getCustomer().getFullName() : null;
        return name != null && !name.isBlank() ? name : "this customer";
    }
}
