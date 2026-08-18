package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import com.novax.leadora.api.dto.request.RoomLineRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    private RoomLineRequest buildValidRoomLine() {
        RoomLineRequest line = new RoomLineRequest();
        line.setProductId(UUID.randomUUID());
        line.setRoomType("Deluxe Double");
        line.setNumberOfRooms(2);
        line.setPricePerNight(BigDecimal.valueOf(1500000));
        return line;
    }

    private CreateQuotationRequest buildValidRequest() {
        CreateQuotationRequest req = new CreateQuotationRequest();
        req.setDealId(UUID.randomUUID());
        req.setRoomLines(new java.util.ArrayList<>(List.of(buildValidRoomLine())));
        req.setCheckInDate(LocalDate.of(2026, 8, 1));
        req.setCheckOutDate(LocalDate.of(2026, 8, 4));
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
    @DisplayName("UT-QUOT-VAL-01b: Multiple room types + quantities pass validation")
    void testMultipleRoomLinesValid() {
        CreateQuotationRequest req = buildValidRequest();
        RoomLineRequest second = new RoomLineRequest();
        second.setProductId(UUID.randomUUID());
        second.setRoomType("Superior Room");
        second.setNumberOfRooms(1);
        second.setPricePerNight(BigDecimal.valueOf(900000));
        req.getRoomLines().add(second);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Multiple valid room lines should have no violations");
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
    @DisplayName("UT-QUOT-VAL-04: Zero rooms on a line → @Min violation")
    void testZeroRoomsTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.getRoomLines().get(0).setNumberOfRooms(0);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("roomLines[0].numberOfRooms")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-05: Negative price on a line → @DecimalMin violation")
    void testNegativePriceTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.getRoomLines().get(0).setPricePerNight(BigDecimal.valueOf(-100));

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("roomLines[0].pricePerNight")));
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
    @DisplayName("UT-QUOT-VAL-07: Missing room type on a line → @NotNull violation on productId")
    void testMissingRoomTypeTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        // The room is identified by productId now, not by the typed-in name: allotment is keyed
        // on the product, and a name could never be matched reliably. Blanking the label is
        // therefore no longer an error - omitting the product is.
        req.getRoomLines().get(0).setProductId(null);

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("roomLines[0].productId")));
    }

    @Test
    @DisplayName("UT-QUOT-VAL-07b: Empty roomLines → @NotEmpty violation")
    void testEmptyRoomLinesTriggersViolation() {
        CreateQuotationRequest req = buildValidRequest();
        req.setRoomLines(List.of());

        Set<ConstraintViolation<CreateQuotationRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roomLines")));
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
