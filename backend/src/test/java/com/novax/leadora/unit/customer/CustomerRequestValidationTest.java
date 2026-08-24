package com.novax.leadora.unit.customer;

import com.novax.leadora.api.dto.request.CreateCustomerRequest;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CustomerRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateCustomerRequest buildValidRequest() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setFullName("Tran Van Customer");
        req.setCustomerType(CustomerType.INDIVIDUAL);
        req.setEmail("customer@hotel.vn");
        req.setPhone("0912345678");
        return req;
    }

    @Test
    @DisplayName("UT-CUST-VAL-01: Valid CreateCustomerRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid customer request should have no violations");
    }

    @Test
    @DisplayName("UT-CUST-VAL-02: Blank full name → @NotBlank violation")
    void testBlankFullNameTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setFullName("");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-03: Null customer type → @NotNull violation")
    void testNullCustomerTypeTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setCustomerType(null);

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerType")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-04: Invalid Vietnamese phone → @Pattern violation")
    void testInvalidPhoneTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setPhone("12345");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-05: Invalid email format → @Email violation")
    void testInvalidEmailTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setEmail("not-an-email");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-06: Blank email → @NotBlank violation")
    void testBlankEmailTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setEmail("");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-07: Blank phone → @NotBlank violation")
    void testBlankPhoneTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setPhone("");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-08: Invalid name format with numbers or symbols → @Pattern violation")
    void testInvalidNamePatternTriggersViolation() {
        CreateCustomerRequest req = buildValidRequest();
        req.setFullName("Customer 123 #$%");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
    }

    @Test
    @DisplayName("UT-CUST-VAL-09: Valid Vietnamese name with diacritics passes")
    void testValidVietnameseNamePasses() {
        CreateCustomerRequest req = buildValidRequest();
        req.setFullName("Nguyễn Văn Ánh Dương");

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Vietnamese names with diacritics should pass validation");
    }
}
