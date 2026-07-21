package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LeadInputValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("UT-LEAD-VAL-01: Valid CreateLeadRequest passes validation")
    void testValidCreateLeadRequest() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("Nguyen Van A");
        request.setEmail("nguyenvana@gmail.com");
        request.setPhone("0912345678");

        Set<ConstraintViolation<CreateLeadRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("UT-LEAD-VAL-02: Blank full name triggers @NotBlank violation")
    void testBlankFullNameTriggersViolation() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("");
        request.setPhone("0912345678");

        Set<ConstraintViolation<CreateLeadRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
    }

    @Test
    @DisplayName("UT-LEAD-VAL-03: Invalid Vietnamese phone number triggers @Pattern violation")
    void testInvalidPhoneTriggersViolation() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("Nguyen Van B");
        request.setPhone("1912345678");

        Set<ConstraintViolation<CreateLeadRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
    }

    @Test
    @DisplayName("UT-LEAD-VAL-04: Invalid email format triggers @Email violation")
    void testInvalidEmailTriggersViolation() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("Nguyen Van C");
        request.setEmail("invalid-email-format");

        Set<ConstraintViolation<CreateLeadRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }
}
