package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateQuotationValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateQuotationRequest buildValidRequest() {
        CreateQuotationRequest req = new CreateQuotationRequest();
        req.setDealId(UUID.randomUUID());
        req.setRoomType("Deluxe Double");
        req.setCheckInDate(LocalDate.of(2026, 8, 1));
        req.setCheckOutDate(LocalDate.of(2026, 8, 4));
        req.setNumberOfRooms(2);
        req.setPricePerNight(BigDecimal.valueOf(1500000));
        req.setDiscountPercent(BigDecimal.valueOf(5));
        req.setValidUntil(LocalDate.of(2026, 9, 1));
        return req;
    }

    @Test
    @DisplayName("UT-QUOT-VAL-01: Valid CreateQuotationRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("UT-QUOT-VAL-02: Null dealId → @NotNull violation")
    void testNullDealIdTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setDealId(null);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dealId")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-03: Null checkInDate → @NotNull violation")
    void testNullCheckInDateTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setCheckInDate(null);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("checkInDate")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-04: Zero rooms → @Min violation")
    void testZeroRoomsTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setNumberOfRooms(0);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("numberOfRooms")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-05: Negative price → @DecimalMin violation")
    void testNegativePriceTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setPricePerNight(BigDecimal.valueOf(-100));

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("pricePerNight")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-06: Discount > 100% → @DecimalMax violation")
    void testDiscountOver100TriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setDiscountPercent(BigDecimal.valueOf(150));

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discountPercent")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-07: Blank room type → @NotBlank violation")
    void testBlankRoomTypeTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setRoomType("");

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roomType")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-08: Null validUntil → @NotNull violation")
    void testNullValidUntilTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setValidUntil(null);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("validUntil")));
    }
}
