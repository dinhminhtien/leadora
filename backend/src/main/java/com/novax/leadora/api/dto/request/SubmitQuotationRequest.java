package com.novax.leadora.api.dto.request;

import lombok.Data;

@Data
public class SubmitQuotationRequest {
    private String submittedByName;
    private String submittedByRole;
}