package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;

/** Customer count per status, computed by the database. */
public record CustomerStatusCount(CustomerStatus status, Long count) {
}
