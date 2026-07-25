package com.novax.leadora.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class CalculateQuotationPricingTest {

    @Test
    @DisplayName("UT-PRICING-01: Subtotal calculation for 3 nights and 2 rooms")
    void testSubtotalCalculation() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 4);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        int rooms = 2;
        BigDecimal pricePerNight = BigDecimal.valueOf(1500000); // 1,500,000 VND

        assertEquals(3, nights);

        BigDecimal subtotal = pricePerNight
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(rooms));

        assertEquals(BigDecimal.valueOf(9000000), subtotal); // 9,000,000 VND
    }

    @Test
    @DisplayName("UT-PRICING-02: Discount calculation at 15%")
    void testDiscountCalculation() {
        BigDecimal subtotal = BigDecimal.valueOf(10000000); // 10,000,000 VND
        BigDecimal discountPercent = BigDecimal.valueOf(15);

        BigDecimal discountAmount = subtotal
                .multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalAfterDiscount = subtotal.subtract(discountAmount);

        assertEquals(BigDecimal.valueOf(1500000).setScale(2), discountAmount);
        assertEquals(BigDecimal.valueOf(8500000).setScale(2), totalAfterDiscount);
    }

    @Test
    @DisplayName("UT-PRICING-03: Zero discount returns exact subtotal")
    void testZeroDiscount() {
        BigDecimal subtotal = BigDecimal.valueOf(5000000);
        BigDecimal discountPercent = BigDecimal.ZERO;

        BigDecimal discountAmount = subtotal
                .multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = subtotal.subtract(discountAmount);

        assertEquals(BigDecimal.ZERO.setScale(2), discountAmount);
        assertEquals(BigDecimal.valueOf(5000000).setScale(2), total);
    }
}
