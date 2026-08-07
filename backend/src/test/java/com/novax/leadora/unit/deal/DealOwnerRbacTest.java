package com.novax.leadora.unit.deal;
import com.novax.leadora.application.usecase.deal.*;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealOwnerRbacTest {

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

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private DealAccessPolicy dealAccessPolicy;

    @Mock
    private ActivityLogPublisher activityLogPublisher;

    // Records the stage transition in the same transaction as the change itself
    // (RecordDealStageChangeService); mocked here because these tests assert on the deal,
    // not on the history row.
    @Mock
    private RecordDealStageChangeService recordDealStageChangeService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CreateDealUseCase createDealUseCase;

    @InjectMocks
    private UpdateDealUseCase updateDealUseCase;

    private UserEntity managerUser;
    private UserEntity staffUser1;
    private UserEntity staffUser2;
    private CustomerEntity customer;

    @BeforeEach
    void setUp() {
        RoleEntity managerRole = RoleEntity.builder().roleName("MANAGER").build();
        RoleEntity staffRole = RoleEntity.builder().roleName("SALES_STAFF").build();

        managerUser = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Manager User")
                .email("manager@example.com")
                .role(managerRole)
                .build();

        staffUser1 = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Staff One")
                .email("staff1@example.com")
                .role(staffRole)
                .build();

        staffUser2 = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Staff Two")
                .email("staff2@example.com")
                .role(staffRole)
                .build();

        customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .fullName("Customer Name")
                .build();
    }

    @Test
    void createDeal_byManager_assignedToStaff_succeeds() {
        // Arrange
        DealRequest request = new DealRequest();
        request.setCustomerId(customer.getCustomerId());
        request.setOwner(staffUser1.getEmail());

        when(customerRepository.findById(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(currentUserProvider.resolve(null)).thenReturn(managerUser);
        when(userRepository.findByEmail(staffUser1.getEmail())).thenReturn(Optional.of(staffUser1));
        when(dealRepository.save(any(DealEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createDealUseCase.execute(request);

        // Assert
        verify(dealRepository).save(argThat(deal -> 
            deal.getAssignedUser() != null && deal.getAssignedUser().getUserId().equals(staffUser1.getUserId())
        ));
    }

    @Test
    void createDeal_byStaff_assignedToSelf_succeeds() {
        // Arrange
        DealRequest request = new DealRequest();
        request.setCustomerId(customer.getCustomerId());
        request.setOwner(staffUser1.getEmail());

        when(customerRepository.findById(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(currentUserProvider.resolve(null)).thenReturn(staffUser1);
        when(userRepository.findByEmail(staffUser1.getEmail())).thenReturn(Optional.of(staffUser1));
        when(dealRepository.save(any(DealEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createDealUseCase.execute(request);

        // Assert
        verify(dealRepository).save(argThat(deal -> 
            deal.getAssignedUser() != null && deal.getAssignedUser().getUserId().equals(staffUser1.getUserId())
        ));
    }

    @Test
    void createDeal_byStaff_assignedToOtherStaff_throwsForbidden() {
        // Arrange
        DealRequest request = new DealRequest();
        request.setCustomerId(customer.getCustomerId());
        request.setOwner(staffUser2.getEmail());

        when(customerRepository.findById(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(currentUserProvider.resolve(null)).thenReturn(staffUser1);
        when(userRepository.findByEmail(staffUser2.getEmail())).thenReturn(Optional.of(staffUser2));

        // Act & Assert
        assertThatThrownBy(() -> createDealUseCase.execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("ROLE_RESTRICTION");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void updateDeal_byManager_changingOwner_succeeds() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        DealEntity existingDeal = DealEntity.builder()
                .dealId(dealId)
                .assignedUser(staffUser1)
                .status(com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus.OPEN)
                .pipelineStage(com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage.INQUIRY)
                .build();

        DealRequest request = new DealRequest();
        request.setOwner(staffUser2.getEmail());

        when(dealRepository.findByIdForUpdate(dealId)).thenReturn(Optional.of(existingDeal));
        when(dealAccessPolicy.currentUser()).thenReturn(managerUser);
        when(userRepository.findByEmail(staffUser2.getEmail())).thenReturn(Optional.of(staffUser2));
        when(dealRepository.save(any(DealEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        updateDealUseCase.execute(dealId, request);

        // Assert
        verify(dealRepository).save(argThat(deal -> 
            deal.getAssignedUser() != null && deal.getAssignedUser().getUserId().equals(staffUser2.getUserId())
        ));
    }

    @Test
    void updateDeal_byStaff_assigningToSelf_succeeds() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        DealEntity existingDeal = DealEntity.builder()
                .dealId(dealId)
                .assignedUser(staffUser2)
                .status(com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus.OPEN)
                .pipelineStage(com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage.INQUIRY)
                .build();

        DealRequest request = new DealRequest();
        request.setOwner(staffUser1.getEmail());

        when(dealRepository.findByIdForUpdate(dealId)).thenReturn(Optional.of(existingDeal));
        when(dealAccessPolicy.currentUser()).thenReturn(staffUser1);
        when(userRepository.findByEmail(staffUser1.getEmail())).thenReturn(Optional.of(staffUser1));
        when(dealRepository.save(any(DealEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        updateDealUseCase.execute(dealId, request);

        // Assert
        verify(dealRepository).save(argThat(deal -> 
            deal.getAssignedUser() != null && deal.getAssignedUser().getUserId().equals(staffUser1.getUserId())
        ));
    }

    @Test
    void updateDeal_byStaff_changingToOtherStaff_throwsForbidden() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        DealEntity existingDeal = DealEntity.builder()
                .dealId(dealId)
                .assignedUser(staffUser1)
                .status(com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus.OPEN)
                .pipelineStage(com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage.INQUIRY)
                .build();

        DealRequest request = new DealRequest();
        request.setOwner(staffUser2.getEmail());

        when(dealRepository.findByIdForUpdate(dealId)).thenReturn(Optional.of(existingDeal));
        when(dealAccessPolicy.currentUser()).thenReturn(staffUser1);
        when(userRepository.findByEmail(staffUser2.getEmail())).thenReturn(Optional.of(staffUser2));

        // Act & Assert
        assertThatThrownBy(() -> updateDealUseCase.execute(dealId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("ROLE_RESTRICTION");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }
}
