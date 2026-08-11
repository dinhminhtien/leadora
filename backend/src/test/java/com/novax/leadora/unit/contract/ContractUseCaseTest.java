package com.novax.leadora.unit.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.application.event.*;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.contract.*;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.config.ContractProperties;
import com.novax.leadora.infrastructure.integration.supabase.SupabaseStorageAdapter;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.ContactRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractUseCaseTest {

    // Common dependencies
    @Mock private ContractRepository contractRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private QuotationDetailRepository quotationDetailRepository;
    @Mock private ContractCodeGenerator contractCodeGenerator;
    @Mock private ActivityLogPublisher activityLogPublisher;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContractConfirmationTokenRepository tokenRepository;
    @Mock private ContractProperties contractProperties;
    @Mock private ContractPdfGenerator pdfGenerator;
    @Mock private SupabaseStorageAdapter storageAdapter;
    @Mock private GetContractByTokenUseCase getContractByTokenUseCase;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    
    private ObjectMapper objectMapper;

    // Use cases under test
    private GenerateContractUseCase generateContractUseCase;
    private SendContractUseCase sendContractUseCase;
    private RequestContractOtpUseCase requestContractOtpUseCase;
    private ConfirmContractOtpUseCase confirmContractOtpUseCase;
    private ActivateContractUseCase activateContractUseCase;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        generateContractUseCase = new GenerateContractUseCase(
                contractRepository,
                contactRepository,
                quotationDetailRepository,
                contractCodeGenerator,
                activityLogPublisher,
                eventPublisher,
                objectMapper
        );

        sendContractUseCase = new SendContractUseCase(
                contractRepository,
                tokenRepository,
                contractProperties,
                pdfGenerator,
                storageAdapter,
                activityLogPublisher,
                eventPublisher
        );

        requestContractOtpUseCase = new RequestContractOtpUseCase(
                contractRepository,
                getContractByTokenUseCase,
                redisTemplate,
                contractProperties,
                eventPublisher
        );

        confirmContractOtpUseCase = new ConfirmContractOtpUseCase(
                contractRepository,
                getContractByTokenUseCase,
                redisTemplate,
                activityLogPublisher,
                eventPublisher
        );

        activateContractUseCase = new ActivateContractUseCase(
                contractRepository,
                activityLogPublisher,
                eventPublisher
        );
    }

    // ==========================================
    // GenerateContractUseCase Tests
    // ==========================================

    @Test
    @DisplayName("UT-GEN-01: Successfully generate a new contract draft for an individual customer")
    void testGenerateIndividualContract() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .customerType(CustomerType.INDIVIDUAL)
                .fullName("John Doe")
                .email("john@example.com")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(UUID.randomUUID())
                .version(1)
                .customer(customer)
                .totalAmount(BigDecimal.valueOf(10000000))
                .validUntil(LocalDate.now().plusDays(7))
                .build();

        UserEntity actor = UserEntity.builder().userId(UUID.randomUUID()).build();

        when(contractRepository.findByQuotation_QuotationId(quotation.getQuotationId()))
                .thenReturn(Collections.emptyList());
        when(contractCodeGenerator.generateCode()).thenReturn("HD-2026-000001");
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractEntity result = generateContractUseCase.execute(quotation, actor);

        assertNotNull(result);
        assertEquals("HD-2026-000001", result.getContractCode());
        assertEquals(1, result.getVersion());
        assertEquals(ContractStatus.DRAFT, result.getStatus());
        assertEquals(CustomerType.INDIVIDUAL, result.getCustomerTypeSnapshot());
        verify(eventPublisher, times(1)).publishEvent(any(ContractGeneratedEvent.class));
    }

    @Test
    @DisplayName("UT-GEN-02: Successfully generate contract with versioning when prior contract exists")
    void testGenerateContractVersions() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .customerType(CustomerType.INDIVIDUAL)
                .fullName("John Doe")
                .email("john@example.com")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(UUID.randomUUID())
                .version(1)
                .customer(customer)
                .totalAmount(BigDecimal.valueOf(10000000))
                .validUntil(LocalDate.now().plusDays(7))
                .build();

        UserEntity actor = UserEntity.builder().userId(UUID.randomUUID()).build();

        ContractEntity existingContract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .version(1)
                .build();

        when(contractRepository.findByQuotation_QuotationId(quotation.getQuotationId()))
                .thenReturn(List.of(existingContract));
        when(contractCodeGenerator.generateCode()).thenReturn("HD-2026-000002");
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractEntity result = generateContractUseCase.execute(quotation, actor);

        assertNotNull(result);
        assertEquals(2, result.getVersion());
        assertEquals(existingContract.getId(), result.getParentContractId());
    }

    // ==========================================
    // SendContractUseCase Tests
    // ==========================================

    @Test
    @DisplayName("UT-SEND-01: Throw exception if contract not in DRAFT status when sending")
    void testSendNonDraftContract() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("alice@example.com")
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.SENT)
                .customer(customer)
                .build();

        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () -> 
                sendContractUseCase.execute(contract.getId())
        );
        assertEquals("INVALID_CONTRACT_STATUS", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("UT-SEND-02: Throw exception if customer email is missing when sending")
    void testSendContractMissingEmail() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email(null)
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.DRAFT)
                .customer(customer)
                .build();

        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () -> 
                sendContractUseCase.execute(contract.getId())
        );
        assertEquals("CUSTOMER_EMAIL_MISSING", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("UT-SEND-03: Successfully send contract (generates PDF, uploads, saves token)")
    void testSendContractSuccess() throws Exception {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .email("alice@example.com")
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .contractCode("HD-2026-000003")
                .status(ContractStatus.DRAFT)
                .customer(customer)
                .build();

        byte[] mockPdf = new byte[]{1, 2, 3};
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(contractRepository.saveAndFlush(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pdfGenerator.generate(any(ContractEntity.class))).thenReturn(mockPdf);
        when(storageAdapter.uploadPdf(anyString(), any(byte[].class))).thenReturn("http://supabase.com/file.pdf");
        when(contractProperties.getPortalBaseUrl()).thenReturn("http://localhost:3000/");

        ContractEntity result = sendContractUseCase.execute(contract.getId());

        assertNotNull(result);
        assertEquals(ContractStatus.SENT, result.getStatus());
        assertEquals("http://supabase.com/file.pdf", result.getPdfUrl());
        assertEquals(PdfStatus.READY, result.getPdfStatus());
        verify(tokenRepository, times(1)).save(any(ContractConfirmationTokenEntity.class));
        verify(eventPublisher, times(1)).publishEvent(any(ContractSentEvent.class));
    }

    // ==========================================
    // RequestContractOtpUseCase Tests
    // ==========================================

    @Test
    @DisplayName("UT-OTP-REQ-01: Successfully request OTP and save in Redis")
    void testRequestOtpSuccess() {
        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.SENT)
                .build();

        String token = "secure-token";
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(contractProperties.getOtpExpirySeconds()).thenReturn(300);

        requestContractOtpUseCase.execute(contract.getId(), token);

        verify(getContractByTokenUseCase, times(1)).validateToken(contract.getId(), token);
        verify(valueOperations, times(1)).set(eq("contract_otp:" + contract.getId()), anyString(), eq(300L), eq(TimeUnit.SECONDS));
        verify(eventPublisher, times(1)).publishEvent(any(ContractOtpRequestedEvent.class));
    }

    // ==========================================
    // ConfirmContractOtpUseCase Tests
    // ==========================================

    @Test
    @DisplayName("UT-OTP-CONF-01: Throw exception if OTP is expired/null")
    void testConfirmExpiredOtp() {
        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.SENT)
                .build();

        String token = "valid-token";
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("contract_otp:" + contract.getId())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                confirmContractOtpUseCase.execute(contract.getId(), token, "123456")
        );
        assertEquals("OTP_EXPIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-OTP-CONF-02: Lock OTP if failed attempts reach threshold")
    void testConfirmOtpMaxFailuresLock() {
        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.SENT)
                .build();

        String token = "valid-token";
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("contract_otp:" + contract.getId())).thenReturn("123456");
        when(valueOperations.increment("contract_otp_fail:" + contract.getId())).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                confirmContractOtpUseCase.execute(contract.getId(), token, "wrong_otp")
        );

        assertEquals("OTP_LOCKED", ex.getErrorCode());
        verify(redisTemplate, times(1)).delete("contract_otp:" + contract.getId());
        verify(redisTemplate, times(1)).delete("contract_otp_fail:" + contract.getId());
    }

    @Test
    @DisplayName("UT-OTP-CONF-03: Successfully acknowledge contract with correct OTP")
    void testConfirmOtpSuccess() {
        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .contractCode("HD-001")
                .status(ContractStatus.SENT)
                .build();

        String token = "valid-token";
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("contract_otp:" + contract.getId())).thenReturn("123456");
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractEntity result = confirmContractOtpUseCase.execute(contract.getId(), token, "123456");

        assertNotNull(result);
        assertEquals(ContractStatus.ACKNOWLEDGED, result.getStatus());
        assertNotNull(result.getAcknowledgedAt());
        verify(redisTemplate, times(1)).delete("contract_otp:" + contract.getId());
        verify(eventPublisher, times(1)).publishEvent(any(ContractAcknowledgedEvent.class));
    }

    // ==========================================
    // ActivateContractUseCase Tests
    // ==========================================

    @Test
    @DisplayName("UT-ACT-01: Successfully activate contract and supersede older ones")
    void testActivateContractWithSupersede() {
        DealEntity deal = DealEntity.builder().dealId(UUID.randomUUID()).build();
        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.ACKNOWLEDGED)
                .deal(deal)
                .version(2)
                .build();

        ContractEntity olderContract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.ACTIVE)
                .deal(deal)
                .version(1)
                .build();

        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(contractRepository.findByDeal_DealId(deal.getDealId())).thenReturn(List.of(contract, olderContract));
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractEntity result = activateContractUseCase.execute(contract.getId());

        assertNotNull(result);
        assertEquals(ContractStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getEffectiveDate());
        assertEquals(ContractStatus.SUPERSEDED, olderContract.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(ContractActivatedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(ContractSupersededEvent.class));
    }
}
