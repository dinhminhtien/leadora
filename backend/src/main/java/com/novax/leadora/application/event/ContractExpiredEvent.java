package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;

public record ContractExpiredEvent(ContractEntity contract) {
}
