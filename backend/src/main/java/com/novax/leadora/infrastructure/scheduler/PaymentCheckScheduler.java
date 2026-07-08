package com.novax.leadora.infrastructure.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novax.leadora.api.dto.request.UpdatePaymentStatusRequest;
import com.novax.leadora.application.usecase.payment.UpdatePaymentStatusUseCase;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Background Scheduler for Direct MB Bank Integration.
 * Pulls transaction history from third-party synchronization APIs (SePay / Casso) to clear payments (BR-41 & BR-29).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCheckScheduler {

    private final PaymentRepository paymentRepository;
    private final UpdatePaymentStatusUseCase updatePaymentStatusUseCase;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final Pattern MEMO_PATTERN = Pattern.compile(
            "LEADORAPAY([0-9A-F-]{15,36})", 
            Pattern.CASE_INSENSITIVE
    );

    @Value("${app.bank.account-number:123456789}")
    private String bankAccountNumber;

    @Value("${app.bank.provider:sepay}") // "sepay" or "casso"
    private String apiProvider;

    @Value("${app.bank.api-token:dummy-token}")
    private String apiToken;

    @Value("${app.bank.api-url:https://my.sepay.vn/plus/api/transactions}")
    private String apiUrl;

    /**
     * Executes direct bank transaction sweep every 15 seconds.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void checkBankTransactions() {
        if ("dummy-token".equals(apiToken.trim())) {
            log.debug("Direct MB Bank integration is in mock mode (no API token configured).");
            return;
        }

        log.debug("Starting direct MB Bank transaction history sweep via Provider: {}", apiProvider);

        List<BankTransaction> transactions = fetchRecentTransactions();
        if (transactions.isEmpty()) {
            return;
        }

        double exchangeRate = fetchUsdVndRate();

        for (BankTransaction txn : transactions) {
            String desc = txn.getDescription();
            if (desc == null) continue;

            // 1. Strict Regex Matching (Memo check)
            Matcher matcher = MEMO_PATTERN.matcher(desc);
            if (!matcher.find()) {
                continue;
            }

            String paymentIdStr = matcher.group(1);
            String cleanPrefix = paymentIdStr.replace("-", "").toLowerCase();
            
            List<PaymentEntity> paymentList = paymentRepository.findByPaymentIdPrefix(cleanPrefix);
            if (paymentList.isEmpty()) {
                log.warn("[SECURITY] Payment request not found for prefix: {}", cleanPrefix);
                continue;
            }
            if (paymentList.size() > 1) {
                log.warn("[SECURITY] Ambiguous prefix match. Multiple payments found for prefix: {}", cleanPrefix);
                continue;
            }

            PaymentEntity payment = paymentList.get(0);
            UUID paymentId = payment.getPaymentId();

            // Ignore if already processed
            if (payment.getStatus() == PaymentStatus.PAID) {
                continue;
            }

            // 2. Idempotency Check (Prevent Replay Attack)
            Optional<PaymentEntity> duplicateOpt = paymentRepository.findByGatewayTransactionId(txn.getReferenceNumber());
            if (duplicateOpt.isPresent()) {
                log.warn("[SECURITY] Replay attack or duplicate transaction detected for Bank Ref: {}. Skipping.", 
                        txn.getReferenceNumber());
                continue;
            }

            // 3. Amount Check
            long expectedAmountVnd = Math.round(payment.getAmount().doubleValue() * exchangeRate);
            long actualAmountVnd = Math.round(txn.getCreditAmount());

            if (expectedAmountVnd != actualAmountVnd) {
                log.warn("[SECURITY] Amount mismatch for Payment {}: Expected {} VND, got {} VND", 
                        paymentId, expectedAmountVnd, actualAmountVnd);
                continue;
            }

            // All checks passed -> confirm the payment as PAID
            try {
                UpdatePaymentStatusRequest updateRequest = new UpdatePaymentStatusRequest();
                updateRequest.setStatus(PaymentStatus.PAID);
                updateRequest.setVerificationNote("Auto-verified via MB Bank (" + apiProvider + ") integration. Ref: " + txn.getReferenceNumber()
                        + ", Amount: " + actualAmountVnd + " VND");
                
                // Map the bank reference number to gateway_transaction_id to prevent future duplicate processing
                payment.setGatewayTransactionId(txn.getReferenceNumber());
                paymentRepository.save(payment);

                // Execute with actor = null (system-triggered bypass)
                updatePaymentStatusUseCase.execute(paymentId, updateRequest, null);
                
                log.info("[AUDIT] Auto-confirmed payment: {} from MB Bank Ref: {}", paymentId, txn.getReferenceNumber());
            } catch (Exception e) {
                log.error("Failed to auto-confirm payment: " + paymentId, e);
            }
        }
    }

    /**
     * Retrieves transactions from SePay or Casso endpoint.
     */
    private List<BankTransaction> fetchRecentTransactions() {
        List<BankTransaction> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            
            if ("casso".equalsIgnoreCase(apiProvider)) {
                headers.set("Authorization", "Apikey " + apiToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<CassoResponse> response = restTemplate.exchange(
                        apiUrl, HttpMethod.GET, entity, CassoResponse.class);
                
                if (response.getBody() != null && response.getBody().getError() == 0 && response.getBody().getData() != null) {
                    List<CassoRecord> records = response.getBody().getData().getRecords();
                    if (records != null) {
                        for (CassoRecord rec : records) {
                            results.add(BankTransaction.builder()
                                    .referenceNumber(rec.getTid())
                                    .creditAmount(rec.getAmount())
                                    .description(rec.getDescription())
                                    .transactionDate(rec.getWhen())
                                    .build());
                        }
                    }
                }
            } else {
                // Default to SePay API
                headers.set("Authorization", "Bearer " + apiToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<SePayResponse> response = restTemplate.exchange(
                        apiUrl, HttpMethod.GET, entity, SePayResponse.class);
                
                if (response.getBody() != null) {
                    List<SePayTransaction> txns = response.getBody().getTransactions();
                    if (txns == null) {
                        txns = response.getBody().getData();
                    }
                    if (txns != null) {
                        for (SePayTransaction t : txns) {
                            // Only count credit transactions (amount_in > 0)
                            if (t.getAmount_in() > 0) {
                                results.add(BankTransaction.builder()
                                        .referenceNumber(t.getReference_number())
                                        .creditAmount(t.getAmount_in())
                                        .description(t.getTransaction_content())
                                        .transactionDate(t.getTransaction_date())
                                        .build());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch transaction logs from " + apiProvider + " API gateway", e);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private double fetchUsdVndRate() {
        try {
            java.util.Map<String, Object> response = restTemplate.getForObject("https://open.er-api.com/v6/latest/USD",
                    java.util.Map.class);
            if (response != null && "success".equals(response.get("result"))) {
                java.util.Map<String, Object> rates = (java.util.Map<String, Object>) response.get("rates");
                if (rates != null && rates.containsKey("VND")) {
                    Number vndRate = (Number) rates.get("VND");
                    return vndRate.doubleValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch real-time exchange rate: {}. Falling back to default 25400.0", e.getMessage());
        }
        return 25400.0;
    }

    @Getter
    @Builder
    public static class BankTransaction {
        private String referenceNumber;
        private double creditAmount;
        private String description;
        private String transactionDate;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SePayResponse {
        private Object status;
        private String messages;
        private List<SePayTransaction> transactions;
        private List<SePayTransaction> data;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SePayTransaction {
        private String reference_number;
        private double amount_in;
        private String transaction_content;
        private String transaction_date;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CassoResponse {
        private int error;
        private String message;
        private CassoData data;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CassoData {
        private List<CassoRecord> records;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CassoRecord {
        private String tid;
        private double amount;
        private String description;
        private String when;
    }
}
