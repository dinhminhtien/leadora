package com.novax.leadora.unit.task;

import com.novax.leadora.api.dto.request.CreateTaskRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateTaskRequest buildValidRequest() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Follow up with VIP client");
        req.setAssignedUserId(UUID.randomUUID());
        req.setPriority("HIGH");
        req.setActivityType("FOLLOW_UP");
        return req;
    }

    @Test
    @DisplayName("UT-TASK-VAL-01: Valid CreateTaskRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid task request should have no violations");
    }

    @Test
    @DisplayName("UT-TASK-VAL-02: Blank title → @NotBlank violation")
    void testBlankTitleTriggersViolation() {
        CreateTaskRequest req = buildValidRequest();
        req.setTitle("");

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    @DisplayName("UT-TASK-VAL-03: Null assignedUserId → @NotNull violation")
    void testNullAssignedUserTriggersViolation() {
        CreateTaskRequest req = buildValidRequest();
        req.setAssignedUserId(null);

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("assignedUserId")));
    }

    @Test
    @DisplayName("UT-TASK-VAL-04: Blank activityType → @NotBlank violation")
    void testBlankActivityTypeTriggersViolation() {
        CreateTaskRequest req = buildValidRequest();
        req.setActivityType("");

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("activityType")));
    }

    @Test
    @DisplayName("UT-TASK-VAL-05: Null priority → @NotNull violation")
    void testNullPriorityTriggersViolation() {
        CreateTaskRequest req = buildValidRequest();
        req.setPriority(null);

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("priority")));
    }
}
