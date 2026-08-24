package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.request.CreateCustomerRequest;
import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.novax.leadora.common.util.TextUtils.blankToNull;

@Service
@RequiredArgsConstructor
public class CreateCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerDuplicatePolicy customerDuplicatePolicy;
    private final CustomerProfilePolicy customerProfilePolicy;
    private final CustomerAccessPolicy customerAccessPolicy;

    @Transactional
    public CustomerResponse execute(CreateCustomerRequest request) {
        // BR-09: a corporate customer profile must name its company (mirrors the same rule
        // enforced for corporate leads). Shared with UpdateCustomerUseCase and ConvertLeadUseCase —
        // see CustomerProfilePolicy for why it is no longer written out in each of them.
        customerProfilePolicy.assertCorporateHasCompany(
                request.getCustomerType(), request.getCompanyName());

        String email = blankToNull(request.getEmail());
        String phone = blankToNull(request.getPhone());

        // Shared with UpdateCustomerUseCase and ConvertLeadUseCase — see CustomerDuplicatePolicy
        // for why this rule lives outside the use cases rather than being repeated in each.
        customerDuplicatePolicy.assertNoDuplicate(email, phone);

        UserEntity currentUser = customerAccessPolicy.currentUser();
        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
        } else if (currentUser != null) {
            assignedUser = currentUser;
        }

        CustomerEntity customer = CustomerEntity.builder()
                .customerType(request.getCustomerType())
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .email(email)
                .phone(phone)
                .companyName(blankToNull(request.getCompanyName()))
                .taxCode(blankToNull(request.getTaxCode()))
                .address(blankToNull(request.getAddress()))
                .assignedUser(assignedUser)
                .createdBy(currentUser)
                .status(CustomerStatus.ACTIVE)
                .build();

        return CustomerResponse.from(customerRepository.save(customer));
    }
}
