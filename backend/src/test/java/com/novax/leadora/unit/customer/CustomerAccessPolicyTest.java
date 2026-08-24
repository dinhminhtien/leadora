package com.novax.leadora.unit.customer;

import com.novax.leadora.application.usecase.customer.CustomerAccessPolicy;
import com.novax.leadora.application.usecase.customer.GetCustomerListUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccessPolicyTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private InteractTimelineRepository timelineRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CustomerRepository customerRepository;

    private CustomerAccessPolicy accessPolicy;
    private GetCustomerListUseCase getCustomerListUseCase;

    private UserEntity salesStaff;
    private UserEntity manager;

    @BeforeEach
    void setUp() {
        accessPolicy = new CustomerAccessPolicy(
                currentUserProvider,
                leadRepository,
                dealRepository,
                timelineRepository,
                taskRepository
        );
        getCustomerListUseCase = new GetCustomerListUseCase(customerRepository, accessPolicy);

        salesStaff = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Staff A")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();

        manager = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Manager")
                .role(RoleEntity.builder().roleName("MANAGER").build())
                .build();
    }

    @Test
    @DisplayName("Manager has unscoped access (listScopeOwnerId returns null)")
    void manager_hasUnscopedAccess() {
        UUID scopedId = accessPolicy.listScopeOwnerId(manager);
        assertThat(scopedId).isNull();
    }

    @Test
    @DisplayName("Sales staff is scoped to their own userId")
    void salesStaff_isScopedToSelf() {
        UUID scopedId = accessPolicy.listScopeOwnerId(salesStaff);
        assertThat(scopedId).isEqualTo(salesStaff.getUserId());
    }

    @Test
    @DisplayName("Sales staff can view customer assigned to them")
    void salesStaff_canViewAssignedCustomer() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .assignedUser(salesStaff)
                .build();

        accessPolicy.assertCanView(salesStaff, customer); // should not throw
    }

    @Test
    @DisplayName("Sales staff can view customer converted/interacted via Lead")
    void salesStaff_canViewCustomerWithRelatedLead() {
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(customerId)
                .build();

        when(leadRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, salesStaff.getUserId()))
                .thenReturn(true);

        accessPolicy.assertCanView(salesStaff, customer); // should not throw
    }

    @Test
    @DisplayName("Sales staff cannot view unrelated customer")
    void salesStaff_cannotViewUnrelatedCustomer() {
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(customerId)
                .assignedUser(UserEntity.builder().userId(UUID.randomUUID()).build())
                .build();

        when(leadRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(leadRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(dealRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(dealRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(timelineRepository.existsByCustomer_CustomerIdAndUser_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(taskRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, salesStaff.getUserId())).thenReturn(false);
        when(taskRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, salesStaff.getUserId())).thenReturn(false);

        assertThatThrownBy(() -> accessPolicy.assertCanView(salesStaff, customer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("GetCustomerListUseCase applies scoped specification for sales staff")
    void getCustomerList_appliesScoping() {
        when(currentUserProvider.resolve(null)).thenReturn(salesStaff);

        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .fullName("My Customer")
                .assignedUser(salesStaff)
                .build();

        when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        Page<?> result = getCustomerListUseCase.execute(null, null, null, "createdAt", "desc", 0, 10);
        assertThat(result.getContent()).hasSize(1);
    }
}
