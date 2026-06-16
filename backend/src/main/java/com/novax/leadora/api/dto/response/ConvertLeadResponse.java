package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ConvertLeadResponse {

    private UUID customerId;
    private LeadResponse lead;
}
