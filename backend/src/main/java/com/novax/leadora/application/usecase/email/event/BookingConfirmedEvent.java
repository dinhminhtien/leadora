package com.novax.leadora.application.usecase.email.event;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import java.util.List;

public record BookingConfirmedEvent(
    BookingEntity booking,
    List<BookingDetailEntity> details
) {}
