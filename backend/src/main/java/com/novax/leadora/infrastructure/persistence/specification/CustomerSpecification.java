package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    public static Specification<CustomerEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("phone")), pattern),
                cb.like(cb.lower(root.get("companyName")), pattern));
    }

    public static Specification<CustomerEntity> hasStatus(CustomerStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<CustomerEntity> hasType(CustomerType type) {
        if (type == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("customerType"), type);
    }

    public static Specification<CustomerEntity> isScopedToUser(UUID staffId) {
        if (staffId == null) {
            return null; // unscoped / full access (Manager/Admin)
        }
        return (root, query, cb) -> {
            Predicate directAssigned = cb.equal(root.get("assignedUser").get("userId"), staffId);
            Predicate directCreated = cb.equal(root.get("createdBy").get("userId"), staffId);

            Subquery<UUID> leadSub = query.subquery(UUID.class);
            Root<LeadEntity> leadRoot = leadSub.from(LeadEntity.class);
            leadSub.select(leadRoot.get("customer").get("customerId"))
                    .where(
                            cb.isNotNull(leadRoot.get("customer")),
                            cb.or(
                                    cb.equal(leadRoot.get("assignedUser").get("userId"), staffId),
                                    cb.equal(leadRoot.get("createdBy").get("userId"), staffId)
                            )
                    );
            Predicate inLead = root.get("customerId").in(leadSub);

            Subquery<UUID> dealSub = query.subquery(UUID.class);
            Root<DealEntity> dealRoot = dealSub.from(DealEntity.class);
            dealSub.select(dealRoot.get("customer").get("customerId"))
                    .where(
                            cb.isNotNull(dealRoot.get("customer")),
                            cb.or(
                                    cb.equal(dealRoot.get("assignedUser").get("userId"), staffId),
                                    cb.equal(dealRoot.get("createdBy").get("userId"), staffId)
                            )
                    );
            Predicate inDeal = root.get("customerId").in(dealSub);

            Subquery<UUID> timelineSub = query.subquery(UUID.class);
            Root<InteractTimelineEntity> timelineRoot = timelineSub.from(InteractTimelineEntity.class);
            timelineSub.select(timelineRoot.get("customer").get("customerId"))
                    .where(
                            cb.isNotNull(timelineRoot.get("customer")),
                            cb.equal(timelineRoot.get("user").get("userId"), staffId)
                    );
            Predicate inTimeline = root.get("customerId").in(timelineSub);

            Subquery<UUID> taskSub = query.subquery(UUID.class);
            Root<TaskEntity> taskRoot = taskSub.from(TaskEntity.class);
            taskSub.select(taskRoot.get("customer").get("customerId"))
                    .where(
                            cb.isNotNull(taskRoot.get("customer")),
                            cb.or(
                                    cb.equal(taskRoot.get("assignedUser").get("userId"), staffId),
                                    cb.equal(taskRoot.get("createdBy").get("userId"), staffId)
                            )
                    );
            Predicate inTask = root.get("customerId").in(taskSub);

            return cb.or(directAssigned, directCreated, inLead, inDeal, inTimeline, inTask);
        };
    }
}
