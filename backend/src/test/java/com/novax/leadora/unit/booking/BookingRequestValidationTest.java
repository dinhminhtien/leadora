package com.novax.leadora.unit.booking;

import com.novax.leadora.api.dto.request.BookingDetailRequest;
import com.novax.leadora.api.dto.request.CreateBookingRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateBookingRequest buildValidRequest() {
        BookingDetailRequest detail = new BookingDetailRequest();
        detail.setProductId(UUID.randomUUID());
        detail.setQuantity(1);
        detail.setUnitPrice(BigDecimal.valueOf(1500000));
        detail.setNights(3);
        detail.setRoomNumber("101");

        return CreateBookingRequest.builder()
                .quotationId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .checkInDate(LocalDate.of(2026, 8, 1))
                .checkOutDate(LocalDate.of(2026, 8, 4))
                .details(List.of(detail))
                .build();
    }

    @Test
    @DisplayName("UT-BOOK-VAL-01: Valid CreateBookingRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid booking request should have no violations");
    }

    @Test
    @DisplayName("UT-BOOK-VAL-02: Null quotationId → @NotNull violation")
    void testNullQuotationIdTriggersViolation() {
        CreateBookingRequest req = buildValidRequest();
        req.setQuotationId(null);

        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("quotationId")));
    }

    @Test
    @DisplayName("UT-BOOK-VAL-03: Null customerId → @NotNull violation")
    void testNullCustomerIdTriggersViolation() {
        CreateBookingRequest req = buildValidRequest();
        req.setCustomerId(null);

        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerId")));
    }

    @Test
    @DisplayName("UT-BOOK-VAL-04: Empty details list → @NotEmpty violation")
    void testEmptyDetailsTriggersViolation() {
        CreateBookingRequest req = buildValidRequest();
        req.setDetails(Collections.emptyList());

        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("details")));
    }

    @Test
    @DisplayName("UT-BOOK-VAL-05: Null checkInDate → @NotNull violation")
    void testNullCheckInDateTriggersViolation() {
        CreateBookingRequest req = buildValidRequest();
        req.setCheckInDate(null);

        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("checkInDate")));
    }
}
