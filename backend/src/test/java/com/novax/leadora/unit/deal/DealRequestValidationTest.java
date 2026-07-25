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
                .stage("PROSPECTING")
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
        request.setPhone("1234567890");

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
}
