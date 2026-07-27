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

    /** Existing documents sharing a title — used to replace an old version on re-upload. */
    List<AiDocumentEntity> findByTitleIgnoreCase(String title);

    /**
     * Titles of the documents that are actually searchable (fully ingested, so {@code chunkCount > 0};
     * excludes rows still processing and the {@code -1} failed sentinel).
     *
     * <p>Returns plain strings, not entities: the caller runs off the request thread and outside any
     * transaction, so a lazy association would blow up on access.
     */
    @Query("SELECT d.title FROM AiDocumentEntity d WHERE d.chunkCount > 0 ORDER BY d.createdAt DESC")
    List<String> findSearchableTitles();
}
