package com.novax.leadora.domain.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class InputValidationTest {

    private static final String PHONE_REGEX = "^(0[35789])\\d{8}$";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,6}$";

    @ParameterizedTest
    @ValueSource(strings = { "0912345678", "0389998888", "0701112222", "0567890123", "0898765432" })
    @DisplayName("UT-VALIDATION-01: Valid Vietnamese 10-digit phone numbers pass regex")
    void testValidVietnamesePhone(String phone) {
        assertTrue(Pattern.matches(PHONE_REGEX, phone), "Phone " + phone + " should be valid");
    }

    @ParameterizedTest
    @ValueSource(strings = { "1912345678", "091234567", "09123456789", "abc1234567", "0123456789", "091234567a" })
    @DisplayName("UT-VALIDATION-02: Invalid phone numbers fail regex")
    void testInvalidVietnamesePhone(String phone) {
        assertFalse(Pattern.matches(PHONE_REGEX, phone), "Phone " + phone + " should be invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = { "user@domain.com", "sales.staff@leadora.vn", "customer_12@sub.domain.co" })
    @DisplayName("UT-VALIDATION-03: Valid email formats pass regex")
    void testValidEmailFormat(String email) {
        assertTrue(Pattern.matches(EMAIL_REGEX, email), "Email " + email + " should be valid");
    }

    @ParameterizedTest
    @ValueSource(strings = { "plainaddress", "@domain.com", "user@.com", "user@domain", "user@domain..com" })
    @DisplayName("UT-VALIDATION-04: Invalid email formats fail regex")
    void testInvalidEmailFormat(String email) {
        assertFalse(Pattern.matches(EMAIL_REGEX, email), "Email " + email + " should be invalid");
    }
}
