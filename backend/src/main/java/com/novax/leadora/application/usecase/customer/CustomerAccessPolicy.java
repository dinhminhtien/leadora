package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Access policy for Customer records.
 *
 * <p>Sales staff can only view and manage customers they are related to
 * (direct assignment, creator, converted lead owner/creator, deal owner/creator, task assignee/creator,
 * or logged interaction timeline).
 * Managers and Admins have full unconstrained access across all customers.
 */
@Component
public class CustomerAccessPolicy extends BaseAccessPolicy<CustomerEntity> {

    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final InteractTimelineRepository timelineRepository;
    private final TaskRepository taskRepository;

    public CustomerAccessPolicy(
            CurrentUserProvider currentUserProvider,
            LeadRepository leadRepository,
            DealRepository dealRepository,
            InteractTimelineRepository timelineRepository,
            TaskRepository taskRepository
    ) {
        super(currentUserProvider);
        this.leadRepository = leadRepository;
        this.dealRepository = dealRepository;
        this.timelineRepository = timelineRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    protected boolean owns(UserEntity user, CustomerEntity customer) {
        if (customer == null) return false;
        UUID uid = user.getUserId();
        if (customer.getAssignedUser() != null && uid.equals(customer.getAssignedUser().getUserId())) {
            return true;
        }
        if (customer.getCreatedBy() != null && uid.equals(customer.getCreatedBy().getUserId())) {
            return true;
        }
        UUID customerId = customer.getCustomerId();
        if (customerId == null) return false;

        // Check if user is assigned or created any lead linked to this customer
        if (leadRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, uid) ||
            leadRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, uid)) {
            return true;
        }

        // Check if user is assigned or created any deal linked to this customer
        if (dealRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, uid) ||
            dealRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, uid)) {
            return true;
        }

        // Check if user has timeline interactions with this customer
        if (timelineRepository.existsByCustomer_CustomerIdAndUser_UserId(customerId, uid)) {
            return true;
        }

        // Check if user has tasks assigned or created for this customer
        if (taskRepository.existsByCustomer_CustomerIdAndAssignedUser_UserId(customerId, uid) ||
            taskRepository.existsByCustomer_CustomerIdAndCreatedBy_UserId(customerId, uid)) {
            return true;
        }

        return false;
    }
}
