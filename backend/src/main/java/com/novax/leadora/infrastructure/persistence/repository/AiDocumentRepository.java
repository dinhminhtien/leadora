package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.AiDocumentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiDocumentRepository extends JpaRepository<AiDocumentEntity, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    @Query("SELECT d FROM AiDocumentEntity d ORDER BY d.createdAt DESC")
    List<AiDocumentEntity> findAllWithUploader();
}
