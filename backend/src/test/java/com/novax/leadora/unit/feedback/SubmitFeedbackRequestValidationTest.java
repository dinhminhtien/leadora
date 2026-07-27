package com.novax.leadora.unit.feedback;

import com.novax.leadora.api.dto.request.SubmitFeedbackRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubmitFeedbackRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private SubmitFeedbackRequest buildValidRequest() {
        SubmitFeedbackRequest req = new SubmitFeedbackRequest();
        req.setRating((short) 4);
        req.setComment("Excellent service");
        req.setRecommendScore(9);
        return req;
    }

    @Test
    @DisplayName("UT-FEEDBACK-VAL-01: Valid request with only basic ratings passes validation")
    void testValidRequestBasic() {
        Set<ConstraintViolation<SubmitFeedbackRequest>> violations = validator.validate(buildValidRequest());
        assertTrue(violations.isEmpty(), "Valid basic request should have no violations");
    }

    @Test
    @DisplayName("UT-FEEDBACK-VAL-02: Valid request with detailed ratings passes validation")
    void testValidRequestDetailed() {
        SubmitFeedbackRequest req = buildValidRequest();
        req.setRatingAttitude((short) 5);
        req.setRatingSpeed((short) 4);
        req.setRatingAccuracy((short) 5);

        Set<ConstraintViolation<SubmitFeedbackRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Valid request with detailed ratings should have no violations");
    }

    @Test
    @DisplayName("UT-FEEDBACK-VAL-03: Invalid detailed ratings (out of range) trigger violations")
    void testInvalidDetailedRatings() {
        SubmitFeedbackRequest req = buildValidRequest();
        req.setRatingAttitude((short) 6); // > 5
        req.setRatingSpeed((short) 0);    // < 1
        req.setRatingAccuracy((short) -1); // < 1

        Set<ConstraintViolation<SubmitFeedbackRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertEquals(3, violations.size());
    }
}
