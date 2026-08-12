package com.novax.leadora.unit.quotation;

import com.novax.leadora.application.event.QuotationOtpRequestedEvent;
import com.novax.leadora.application.usecase.quotation.*;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.config.QuotationProperties;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationOtpAuditEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationOtpAuditRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.application.usecase.contract.GenerateContractUseCase;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.novax.leadora.common.security.OtpStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationOtpUseCaseTest {

    @Mock private QuotationRepository quotationRepository;
    @Mock private GetQuotationByTokenUseCase getQuotationByTokenUseCase;
    @Mock private OtpStore otpStore;
    @Mock private QuotationProperties quotationProperties;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private QuotationOtpAuditRepository auditRepository;
    @Mock private CustomerAcceptQuotationUseCase customerAcceptQuotationUseCase;
    @Mock private GenerateContractUseCase generateContractUseCase;
    @Mock private ContractRepository contractRepository;

    private RequestQuotationOtpUseCase requestQuotationOtpUseCase;
    private ConfirmQuotationOtpUseCase confirmQuotationOtpUseCase;

    @BeforeEach
    void setUp() {
        requestQuotationOtpUseCase = new RequestQuotationOtpUseCase(
                quotationRepository,
                getQuotationByTokenUseCase,
                otpStore,
                quotationProperties,
                eventPublisher
        );

        confirmQuotationOtpUseCase = new ConfirmQuotationOtpUseCase(
                quotationRepository,
                getQuotationByTokenUseCase,
                otpStore,
                auditRepository,
                customerAcceptQuotationUseCase,
                generateContractUseCase,
                contractRepository
        );
    }

    @Test
    @DisplayName("UT-Q-OTP-REQ-01: Successfully request OTP and store in Redis")
    void testRequestQuotationOtpSuccess() {
        UUID quotationId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("cust@example.com")
                .fullName("Jane Doe")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .customer(customer)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .validUntil(LocalDate.now().plusDays(2))
                .build();

        String token = "secure-token";
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(quotationProperties.getOtpExpirySeconds()).thenReturn(900);

        requestQuotationOtpUseCase.execute(quotationId, token);

        verify(getQuotationByTokenUseCase, times(1)).validateToken(quotationId, token);
        verify(otpStore, times(1)).put(eq("quotation_otp:" + quotationId), anyString(), eq(Duration.ofSeconds(900)));
        verify(eventPublisher, times(1)).publishEvent(any(QuotationOtpRequestedEvent.class));
    }

    @Test
    @DisplayName("UT-Q-OTP-CONF-01: Confirm OTP throws BusinessException if OTP is expired or null")
    void testConfirmOtpExpiredOrNull() {
        UUID quotationId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("cust@example.com")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .customer(customer)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .build();

        String token = "secure-token";
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(otpStore.get("quotation_otp:" + quotationId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                confirmQuotationOtpUseCase.execute(quotationId, token, "123456", "127.0.0.1", "Mozilla/5.0")
        );

        assertEquals("OTP_EXPIRED", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(auditRepository, times(1)).save(any(QuotationOtpAuditEntity.class));
        verify(getQuotationByTokenUseCase, times(1)).validateToken(quotationId, token);
    }

    @Test
    @DisplayName("UT-Q-OTP-CONF-02: Confirm OTP locks after 5 failed attempts")
    void testConfirmOtpLockOnFailureThreshold() {
        UUID quotationId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("cust@example.com")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .customer(customer)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .build();

        String token = "secure-token";
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(otpStore.get("quotation_otp:" + quotationId)).thenReturn("123456");
        when(otpStore.increment(eq("quotation_otp_fail:" + quotationId), any(Duration.class))).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                confirmQuotationOtpUseCase.execute(quotationId, token, "incorrect_otp", "127.0.0.1", "Mozilla/5.0")
        );

        assertEquals("OTP_LOCKED", ex.getErrorCode());
        verify(otpStore, times(1)).delete("quotation_otp:" + quotationId);
        verify(otpStore, times(1)).delete("quotation_otp_fail:" + quotationId);
        verify(auditRepository, times(1)).save(any(QuotationOtpAuditEntity.class));
        verify(getQuotationByTokenUseCase, times(1)).validateToken(quotationId, token);
    }

    @Test
    @DisplayName("UT-Q-OTP-CONF-03: Successfully confirm OTP, transition status to RESERVATION_PENDING")
    void testConfirmOtpSuccess() {
        UUID quotationId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("cust@example.com")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .customer(customer)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .build();

        String token = "secure-token";
        ContractEntity dummyContract = ContractEntity.builder().build();
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(otpStore.get("quotation_otp:" + quotationId)).thenReturn("123456");

        QuotationEntity acceptedQuotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .customer(customer)
                .status(QuotationStatus.RESERVATION_PENDING)
                .build();
        when(customerAcceptQuotationUseCase.execute(eq(quotationId), eq("127.0.0.1"), eq("Mozilla/5.0")))
                .thenReturn(acceptedQuotation);

        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(new java.util.ArrayList<>());
        when(generateContractUseCase.execute(any(QuotationEntity.class), any())).thenReturn(dummyContract);

        QuotationEntity result = confirmQuotationOtpUseCase.execute(quotationId, token, "123456", "127.0.0.1", "Mozilla/5.0");

        assertNotNull(result);
        assertEquals(QuotationStatus.RESERVATION_PENDING, result.getStatus());
        verify(otpStore, times(1)).delete("quotation_otp:" + quotationId);
        verify(otpStore, times(1)).delete("quotation_otp_fail:" + quotationId);
        verify(customerAcceptQuotationUseCase, times(1)).execute(eq(quotationId), eq("127.0.0.1"), eq("Mozilla/5.0"));
        verify(auditRepository, times(1)).save(any(QuotationOtpAuditEntity.class));
        verify(getQuotationByTokenUseCase, times(1)).validateToken(quotationId, token);
    }
}
