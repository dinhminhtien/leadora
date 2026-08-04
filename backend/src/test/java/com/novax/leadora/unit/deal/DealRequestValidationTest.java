package com.novax.leadora.unit.deal;

import com.novax.leadora.api.dto.request.DealRequest;
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

import static org.junit.jupiter.api.Assertions.*;

class DealRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private DealRequest buildValidRequest() {
        return DealRequest.builder()
                .title("Wedding Event Q4")
                .contactName("Nguyen Van A")
                .email("contact@hotel.vn")
                .phone("0912345678")
                .value(BigDecimal.valueOf(50000000))
                .stage("INQUIRY")
                .expectedClose(LocalDate.of(2026, 12, 31))
                .build();
    }

    @Test
    @DisplayName("UT-DEAL-REQ-01: Valid DealRequest passes validation")
    void testValidDealRequest() {
        Set<ConstraintViolation<DealRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid DealRequest should have no violations");
    }

    @Test
    @DisplayName("UT-DEAL-REQ-02: Blank title → @NotBlank violation")
    void testBlankTitleTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setTitle("");

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    @DisplayName("UT-DEAL-REQ-03: Blank contact name → @NotBlank violation")
    void testBlankContactNameTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setContactName("");

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("contactName")));
    }

    @Test
    @DisplayName("UT-DEAL-REQ-04: Negative value → @DecimalMin violation")
    void testNegativeValueTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setValue(BigDecimal.valueOf(-100));

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("value")));
    }

    @Test
    @DisplayName("UT-DEAL-REQ-05: Invalid Vietnamese phone → @Pattern violation")
    void testInvalidPhoneTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setPhone("12345");

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
    }

    @Test
    @DisplayName("UT-DEAL-REQ-06: Null expected close date → @NotNull violation")
    void testNullExpectedCloseTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setExpectedClose(null);

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("expectedClose")));
    }

    @Test
    @DisplayName("UT-DEAL-REQ-07: Invalid email format → @Email violation")
    void testInvalidEmailTriggersViolation() {
        DealRequest request = buildValidRequest();
        request.setEmail("not-valid-email");

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    /**
     * The customer this deal is for may simply have no phone on file — which is exactly what the
     * post-conversion "Create Deal" panel forwards from a walk-in lead. The pattern used to have no
     * empty branch, so an optional field refused the only value it could carry.
     */
    @Test
    @DisplayName("UT-DEAL-REQ-08: A missing phone is accepted — it is an optional field")
    void testBlankPhoneIsAccepted() {
        DealRequest request = buildValidRequest();
        request.setPhone("");

        assertTrue(validator.validate(request).isEmpty());

        request.setPhone(null);
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("UT-DEAL-REQ-09: A malformed phone is still rejected")
    void testMalformedPhoneStillRejected() {
        DealRequest request = buildValidRequest();
        request.setPhone("abc123");

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
    }

    /**
     * {@code deals.deal_name} is {@code VARCHAR(50)}. Anything the DTO lets through above that
     * reaches the database and comes back as an unexplained 500 instead of a field-level message.
     */
    @Test
    @DisplayName("UT-DEAL-REQ-10: A title longer than the deal_name column is rejected by validation")
    void testOverlongTitleRejected() {
        DealRequest request = buildValidRequest();
        request.setTitle("x".repeat(51));

        Set<ConstraintViolation<DealRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));

        request.setTitle("x".repeat(50));
        assertTrue(validator.validate(request).isEmpty());
    }
}
