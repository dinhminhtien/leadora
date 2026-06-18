package com.novax.leadora.api.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExpireOverdueRequest {
    private String expiredByName;
    private String expiredByRole;
}
