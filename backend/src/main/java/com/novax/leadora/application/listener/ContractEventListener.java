package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.ContractOtpRequestedEvent;
import com.novax.leadora.application.event.ContractSentEvent;
import com.novax.leadora.application.usecase.contract.ContractEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventListener {

    private final ContractEmailService contractEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContractSent(ContractSentEvent event) {
        log.info("ContractSentEvent received. Sending secure link email to {} for contract {}", 
                event.contract().getSentTo(), event.contract().getContractCode());
        try {
            contractEmailService.sendSecureLinkEmail(event.contract(), event.secureLink(), event.pdfBytes());
        } catch (Exception e) {
            log.error("Failed to send secure link email inside listener: {}", e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContractOtpRequested(ContractOtpRequestedEvent event) {
        log.info("ContractOtpRequestedEvent received. Sending OTP email to {} for contract {}", 
                event.contract().getSentTo(), event.contract().getContractCode());
        try {
            contractEmailService.sendOtpEmail(event.contract(), event.otpCode());
        } catch (Exception e) {
            log.error("Failed to send OTP email inside listener: {}", e.getMessage(), e);
        }
    }
}
