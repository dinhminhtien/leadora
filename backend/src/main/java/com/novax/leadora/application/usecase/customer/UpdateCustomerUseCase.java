package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.request.UpdateCustomerRequest;
import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerDuplicatePolicy customerDuplicatePolicy;

    @Transactional
    public CustomerResponse execute(UUID customerId, UpdateCustomerRequest request) {
        CustomerEntity customer = customerRepository.findByIdWithUsers(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        // Shared rule (see CustomerDuplicatePolicy). Passing this customer's own id lets the policy
        // ignore a match against itself, which is what the two "skip if unchanged" guards used to
        // do by hand — and it also covers the case they missed: changing the CASE of an existing
        // phone, or re-saving an email that differs only in whitespace.
        customerDuplicatePolicy.assertNoDuplicate(
                request.getEmail(), request.getPhone(), customer.getCustomerId());

        customer.setFullName(request.getFullName());
        if (request.getCustomerType() != null) {
            customer.setCustomerType(request.getCustomerType());
        }
        customer.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        customer.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone() : null);
        customer.setCompanyName(StringUtils.hasText(request.getCompanyName()) ? request.getCompanyName() : null);
        customer.setTaxCode(StringUtils.hasText(request.getTaxCode()) ? request.getTaxCode() : null);
        customer.setAddress(StringUtils.hasText(request.getAddress()) ? request.getAddress() : null);
        if (request.getStatus() != null) {
            customer.setStatus(request.getStatus());
        }

        if (request.getAssignedUserId() != null) {
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
            customer.setAssignedUser(assignedUser);
        }

        // BR-09: validate the resulting state — a corporate customer must name its
        // company (either or both of type / companyName may have just changed).
        if (customer.getCustomerType() == CustomerType.CORPORATE
                && !StringUtils.hasText(customer.getCompanyName())) {
            throw new BusinessException(
                    "CUSTOMER_COMPANY_REQUIRED",
                    "Company name is required for a corporate customer.",
                    HttpStatus.BAD_REQUEST);
        }

        return CustomerResponse.from(customerRepository.save(customer));
    }
}
