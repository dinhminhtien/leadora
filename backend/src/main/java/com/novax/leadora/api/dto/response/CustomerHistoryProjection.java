package com.novax.leadora.api.dto.response;

import java.math.BigDecimal;

public interface CustomerHistoryProjection {
    String getType();
    String getId();
    String getTitle();
    String getStatus();
    String getStage();
    BigDecimal getAmount();
    String getCheckIn();
    String getCheckOut();
    String getExpectedClose();
    String getCreatedAt();
    String getNotes();
}
