package com.novax.leadora.integration.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.application.usecase.deal.CreateDealUseCase;
import com.novax.leadora.application.usecase.deal.DealMapper;
import com.novax.leadora.application.usecase.deal.DealValidation;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealIntegrationTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DealMapper dealMapper;

    @Mock
    private DealValidation dealValidation;

    @InjectMocks
    private CreateDealUseCase createDealUseCase;

    @Test
    @DisplayName("IT-DEAL-01: Create deal with valid customer → persists and returns DealResponse")
    void testCreateDealSuccess() {
        UUID customerId = UUID.randomUUID();
        DealRequest request = DealRequest.builder()
                .customerId(customerId)
                .title("Wedding Q4 2026")
                .contactName("Nguyen Van A")
                .email("contact@hotel.vn")
                .stage("PROSPECTING")
                .value(BigDecimal.valueOf(100000000))
                .expectedClose(LocalDate.of(2026, 12, 31))
                .build();

        CustomerEntity customer = CustomerEntity.builder()
                .customerId(customerId)
                .fullName("Customer A")
                .build();

        UserEntity defaultUser = UserEntity.builder().userId(UUID.randomUUID()).build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dealMapper.mapStageToEnum("PROSPECTING")).thenReturn(DealPipelineStage.PROSPECTING);
        when(dealMapper.mapStatusToEnum(null)).thenReturn(DealStatus.OPEN);
        when(userRepository.findAll()).thenReturn(List.of(defaultUser));
        when(dealRepository.save(any(DealEntity.class))).thenAnswer(inv -> {
            DealEntity d = inv.getArgument(0);
            d.setDealId(UUID.randomUUID());
            return d;
        });
        when(dealMapper.mapToResponse(any(DealEntity.class))).thenReturn(new DealResponse());

        DealResponse response = createDealUseCase.execute(request);

        assertNotNull(response);
        verify(dealRepository).save(any(DealEntity.class));
    }

    @Test
    @DisplayName("IT-DEAL-02: Create deal without customerId → throws IllegalArgumentException")
    void testCreateDealWithoutCustomerIdThrows() {
        DealRequest request = DealRequest.builder()
                .customerId(null)
                .title("Wedding Q4 2026")
                .contactName("Nguyen Van A")
                .stage("PROSPECTING")
                .expectedClose(LocalDate.of(2026, 12, 31))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> createDealUseCase.execute(request));
    }

    @Test
    @DisplayName("IT-DEAL-03: Create deal with non-existent customer → throws ResourceNotFoundException")
    void testCreateDealWithNonExistentCustomerThrows() {
        UUID customerId = UUID.randomUUID();
        DealRequest request = DealRequest.builder()
                .customerId(customerId)
                .title("Wedding Q4 2026")
                .contactName("Nguyen Van A")
                .stage("PROSPECTING")
                .expectedClose(LocalDate.of(2026, 12, 31))
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> createDealUseCase.execute(request));
    }
}
