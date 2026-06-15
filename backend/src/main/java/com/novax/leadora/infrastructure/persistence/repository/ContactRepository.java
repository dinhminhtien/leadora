package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<ContactEntity, UUID> {
    List<ContactEntity> findByCustomer_CustomerId(UUID customerId);
    Optional<ContactEntity> findByCustomer_CustomerIdAndIsPrimaryTrue(UUID customerId);
}
