package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.request.CreateCustomerRequest;
import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CreateCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Transactional
    public CustomerResponse execute(CreateCustomerRequest request) {
        // BR-09: a corporate customer profile must name its company (mirrors the
        // same rule enforced for corporate leads).
        if (request.getCustomerType() == CustomerType.CORPORATE
                && !StringUtils.hasText(request.getCompanyName())) {
            throw new BusinessException(
                    "CUSTOMER_COMPANY_REQUIRED",
                    "Company name is required for a corporate customer.",
                    HttpStatus.BAD_REQUEST);
        }

        // Duplicate email check
        if (StringUtils.hasText(request.getEmail())) {
            customerRepository.findFirstByEmail(request.getEmail()).ifPresent(existing -> {
                throw new BusinessException(
                        "DUPLICATE_CUSTOMER_EMAIL",
                        "A customer with email '" + request.getEmail() + "' already exists.",
                        HttpStatus.CONFLICT);
            });
        }

        // Duplicate phone check
        if (StringUtils.hasText(request.getPhone())) {
            customerRepository.findFirstByPhone(request.getPhone()).ifPresent(existing -> {
                throw new BusinessException(
                        "DUPLICATE_CUSTOMER_PHONE",
                        "A customer with phone '" + request.getPhone() + "' already exists.",
                        HttpStatus.CONFLICT);
            });
        }

        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
        }

        CustomerEntity customer = CustomerEntity.builder()
                .customerType(request.getCustomerType())
                .fullName(request.getFullName())
                .email(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null)
                .phone(StringUtils.hasText(request.getPhone()) ? request.getPhone() : null)
                .companyName(StringUtils.hasText(request.getCompanyName()) ? request.getCompanyName() : null)
                .taxCode(StringUtils.hasText(request.getTaxCode()) ? request.getTaxCode() : null)
                .address(StringUtils.hasText(request.getAddress()) ? request.getAddress() : null)
                .assignedUser(assignedUser)
                .status(CustomerStatus.ACTIVE)
                .build();

        return CustomerResponse.from(customerRepository.save(customer));
    }
}
