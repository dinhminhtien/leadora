package com.novax.leadora.unit.identity;

import com.novax.leadora.api.dto.request.LoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("UT-LOGIN-VAL-01: Valid LoginRequest passes validation")
    void testValidLoginRequest() {
        LoginRequest request = LoginRequest.builder()
                .email("admin@leadora.vn")
                .password("Password123!")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("UT-LOGIN-VAL-02: Blank email triggers @NotBlank violation")
    void testBlankEmailTriggersViolation() {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("Password123!")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("UT-LOGIN-VAL-03: Invalid email format triggers @Email violation")
    void testInvalidEmailFormatTriggersViolation() {
        LoginRequest request = LoginRequest.builder()
                .email("not-an-email")
                .password("Password123!")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("UT-LOGIN-VAL-04: Blank password triggers @NotBlank violation")
    void testBlankPasswordTriggersViolation() {
        LoginRequest request = LoginRequest.builder()
                .email("admin@leadora.vn")
                .password("")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    @DisplayName("UT-LOGIN-VAL-05: Null email triggers violation")
    void testNullEmailTriggersViolation() {
        LoginRequest request = LoginRequest.builder()
                .email(null)
                .password("Password123!")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }
}
