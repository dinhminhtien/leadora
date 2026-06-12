package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductServiceRepository extends JpaRepository<ProductServiceEntity, UUID> {
    List<ProductServiceEntity> findByCategory(ProductCategory category);
    List<ProductServiceEntity> findByStatus(ProductStatus status);
}
