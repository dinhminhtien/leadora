package com.novax.leadora.unit.reminder;

import com.novax.leadora.api.dto.request.CreateReminderRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReminderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateReminderRequest buildValidRequest() {
        CreateReminderRequest req = new CreateReminderRequest();
        req.setTitle("Follow up quotation");
        req.setRemindAt(OffsetDateTime.now().plusDays(3));
        req.setPriority("HIGH");
        req.setRelatedEntity("QUOTATION");
        req.setRelatedId(UUID.randomUUID());
        return req;
    }

    @Test
    @DisplayName("UT-REM-VAL-01: Valid CreateReminderRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid reminder request should have no violations");
    }

    @Test
    @DisplayName("UT-REM-VAL-02: Past remindAt → @Future violation")
    void testPastRemindAtTriggersViolation() {
        CreateReminderRequest req = buildValidRequest();
        req.setRemindAt(OffsetDateTime.now().minusDays(1));

        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("remindAt")));
    }

    @Test
    @DisplayName("UT-REM-VAL-03: Invalid priority pattern → @Pattern violation")
    void testInvalidPriorityTriggersViolation() {
        CreateReminderRequest req = buildValidRequest();
        req.setPriority("URGENT");

        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("priority")));
    }

    @Test
    @DisplayName("UT-REM-VAL-04: Invalid relatedEntity → @Pattern violation")
    void testInvalidRelatedEntityTriggersViolation() {
        CreateReminderRequest req = buildValidRequest();
        req.setRelatedEntity("INVALID_TYPE");

        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("relatedEntity")));
    }

    @Test
    @DisplayName("UT-REM-VAL-05: Blank title → @NotBlank violation")
    void testBlankTitleTriggersViolation() {
        CreateReminderRequest req = buildValidRequest();
        req.setTitle("");

        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    @DisplayName("UT-REM-VAL-06: Null relatedId → @NotNull violation")
    void testNullRelatedIdTriggersViolation() {
        CreateReminderRequest req = buildValidRequest();
        req.setRelatedId(null);

        Set<ConstraintViolation<CreateReminderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("relatedId")));
    }
}
