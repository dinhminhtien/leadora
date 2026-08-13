package com.novax.leadora.infrastructure.persistence.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface StaffDealPerformanceProjection {
    UUID getStaffId();
    long getTotalDeals();
    long getWonDeals();
    long getLostDeals();
    BigDecimal getTotalRevenueWon();
}
