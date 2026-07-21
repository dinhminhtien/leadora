package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.application.usecase.quotation.ExpireOverdueQuotationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * UC-14.8 step 1-2: auto-expire quotations whose validity period has passed.
 * Previously this only ran on-demand via POST /quotations/expire-overdue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireOverdueQuotationsScheduler {

    private final ExpireOverdueQuotationsUseCase expireOverdueQuotationsUseCase;

    @Scheduled(cron = "0 5 0 * * *")
    public void expireOverdueQuotations() {
        try {
            Map<String, Object> result = expireOverdueQuotationsUseCase.execute(new ExpireOverdueRequest());
            int expiredCount = (int) result.getOrDefault("expiredCount", 0);
            if (expiredCount > 0) {
                log.info("Quotation expiry scan: {} quotation(s) auto-expired", expiredCount);
            }
        } catch (Exception e) {
            log.error("Quotation expiry scheduler error: {}", e.getMessage(), e);
        }
    }
}
