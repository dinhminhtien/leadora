package com.novax.leadora.unit.sla;

import com.novax.leadora.api.dto.request.SlaRuleRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SlaRuleRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private SlaRuleRequest buildValidRequest() {
        SlaRuleRequest req = new SlaRuleRequest();
        req.setActivityType("LEAD_RESPONSE");
        req.setName("Lead Response Time SLA");
        req.setDeadlineHours(24);
        req.setWarningThreshold(18);
        req.setEscalationThreshold(22);
        req.setActive(true);
        return req;
    }

    @Test
    @DisplayName("UT-SLA-VAL-01: Valid SlaRuleRequest passes validation")
    void testValidRequest() {
        Set<ConstraintViolation<SlaRuleRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid SLA rule request should have no violations");
    }

    @Test
    @DisplayName("UT-SLA-VAL-02: Blank activity type → @NotBlank violation")
    void testBlankActivityTypeTriggersViolation() {
        SlaRuleRequest req = buildValidRequest();
        req.setActivityType("");

        Set<ConstraintViolation<SlaRuleRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("activityType")));
    }

    @Test
    @DisplayName("UT-SLA-VAL-03: Blank name → @NotBlank violation")
    void testBlankNameTriggersViolation() {
        SlaRuleRequest req = buildValidRequest();
        req.setName("");

        Set<ConstraintViolation<SlaRuleRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    @DisplayName("UT-SLA-VAL-04: Zero deadline hours → @Min violation")
    void testZeroDeadlineHoursTriggersViolation() {
        SlaRuleRequest req = buildValidRequest();
        req.setDeadlineHours(0);

        Set<ConstraintViolation<SlaRuleRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("deadlineHours")));
    }

    @Test
    @DisplayName("UT-SLA-VAL-05: Deadline exceeds 8760 hours → @Max violation")
    void testExcessiveDeadlineTriggersViolation() {
        SlaRuleRequest req = buildValidRequest();
        req.setDeadlineHours(9999);

        Set<ConstraintViolation<SlaRuleRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("deadlineHours")));
    }
}
