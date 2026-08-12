package com.novax.leadora.infrastructure.persistence.repository.projection;

import java.util.UUID;

public interface StaffFeedbackPerformanceProjection {
    UUID getStaffId();
    long getTotalFeedbacks();
    long getTotalAnalyzedFeedbacks();
    long getPositiveFeedbacks();
    long getNeutralFeedbacks();
    long getNegativeFeedbacks();
    
    long getAttitudePos();
    long getAttitudeCount();
    long getSpeedPos();
    long getSpeedCount();
    long getAccuracyPos();
    long getAccuracyCount();
    long getFacilityPos();
    long getFacilityCount();
    long getPricePos();
    long getPriceCount();
}
