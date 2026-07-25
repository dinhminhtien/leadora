package com.novax.leadora.unit.customer;

import com.novax.leadora.api.dto.request.CreateCustomerRequest;
import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.application.usecase.customer.CreateCustomerUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateCustomerUseCaseTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreateCustomerUseCase createCustomerUseCase;

    private CreateCustomerRequest buildValidRequest() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setFullName("Tran Van B");
        req.setCustomerType(CustomerType.INDIVIDUAL);
        req.setEmail("tranvanb@hotel.vn");
        req.setPhone("0912345678");
        return req;
    }

    @Test
    @DisplayName("UT-CUST-01: Valid individual customer → creates successfully")
    void testCreateCustomerSuccess() {
        CreateCustomerRequest request = buildValidRequest();

        when(customerRepository.findFirstByEmail("tranvanb@hotel.vn")).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone("0912345678")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setCustomerId(UUID.randomUUID());
            return c;
        });

        CustomerResponse response = createCustomerUseCase.execute(request);

        assertNotNull(response);
        verify(customerRepository).save(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("UT-CUST-02: Corporate customer without company name → throws CUSTOMER_COMPANY_REQUIRED")
    void testCorporateWithoutCompanyThrows() {
        CreateCustomerRequest request = buildValidRequest();
        request.setCustomerType(CustomerType.CORPORATE);
        request.setCompanyName(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> createCustomerUseCase.execute(request));
        assertEquals("CUSTOMER_COMPANY_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CUST-03: Duplicate email → throws DUPLICATE_CUSTOMER_EMAIL")
    void testDuplicateEmailThrows() {
        CreateCustomerRequest request = buildValidRequest();

        when(customerRepository.findFirstByEmail("tranvanb@hotel.vn"))
                .thenReturn(Optional.of(CustomerEntity.builder().build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> createCustomerUseCase.execute(request));
        assertEquals("DUPLICATE_CUSTOMER_EMAIL", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CUST-04: Duplicate phone → throws DUPLICATE_CUSTOMER_PHONE")
    void testDuplicatePhoneThrows() {
        CreateCustomerRequest request = buildValidRequest();

        when(customerRepository.findFirstByEmail("tranvanb@hotel.vn")).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone("0912345678"))
                .thenReturn(Optional.of(CustomerEntity.builder().build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> createCustomerUseCase.execute(request));
        assertEquals("DUPLICATE_CUSTOMER_PHONE", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CUST-05: Corporate customer with company name → creates successfully")
    void testCorporateCustomerSuccess() {
        CreateCustomerRequest request = buildValidRequest();
        request.setCustomerType(CustomerType.CORPORATE);
        request.setCompanyName("Novax Corp");

        when(customerRepository.findFirstByEmail("tranvanb@hotel.vn")).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone("0912345678")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setCustomerId(UUID.randomUUID());
            return c;
        });

        CustomerResponse response = createCustomerUseCase.execute(request);

        assertNotNull(response);
    }
}
