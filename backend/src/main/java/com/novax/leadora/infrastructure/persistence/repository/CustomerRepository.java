package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID>, JpaSpecificationExecutor<CustomerEntity> {

    // ── Paginated list (dynamic filtering via Specification) ───────────────

    /**
     * Overrides JpaSpecificationExecutor to apply the customer EntityGraph on every
     * paginated Specification query. Associations are @ManyToOne, so no in-memory
     * pagination issues. Sort is driven by the Pageable passed from the use case.
     */
    @Override
    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    Page<CustomerEntity> findAll(Specification<CustomerEntity> spec, Pageable pageable);

    // ── Lightweight autocomplete (no relation joins needed) ────────────────

    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email)       LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone)       LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    Page<CustomerEntity> searchCustomers(@Param("search") String search, Pageable pageable);

    // ── Single entity fetch ────────────────────────────────────────────────

    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    @Query("SELECT c FROM CustomerEntity c WHERE c.customerId = :id")
    Optional<CustomerEntity> findByIdWithUsers(@Param("id") UUID id);

    // ── Lightweight relation lookups (no eager load required) ─────────────

    List<CustomerEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<CustomerEntity> findByStatus(CustomerStatus status);
    long countByStatus(CustomerStatus status);
    long countByCustomerType(CustomerType customerType);
    Optional<CustomerEntity> findFirstByEmail(String email);
    Optional<CustomerEntity> findFirstByPhone(String phone);
    Optional<CustomerEntity> findFirstByFullName(String fullName);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────

    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE (:userId IS NULL OR c.assignedUser.userId = :userId)
            ORDER BY c.createdAt DESC
            """)
    List<CustomerEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT 'DEAL' AS type, d.deal_id::text AS id, d.deal_name AS title, d.status::text AS status, d.pipeline_stage::text AS stage, d.expected_revenue AS amount, null AS "checkIn", null AS "checkOut", d.expected_close_date::text AS "expectedClose", d.created_at::text AS "createdAt", d.notes AS notes
            FROM deals d WHERE d.customer_id = :customerId
            UNION ALL
            SELECT 'BOOKING' AS type, b.booking_id::text AS id, COALESCE('Booking #' || b.booking_code, 'Booking') AS title, b.status::text AS status, null AS stage, b.total_amount AS amount, b.check_in_date::text AS "checkIn", b.check_out_date::text AS "checkOut", null AS "expectedClose", b.created_at::text AS "createdAt", b.special_requests AS notes
            FROM bookings b WHERE b.customer_id = :customerId
            UNION ALL
            SELECT 'QUOTATION' AS type, q.quotation_id::text AS id, 'Quotation v' || COALESCE(q.version, 1) || COALESCE(' – ' || q.room_type, '') AS title, q.status::text AS status, null AS stage, q.total_amount AS amount, q.check_in_date::text AS "checkIn", q.check_out_date::text AS "checkOut", null AS "expectedClose", q.created_at::text AS "createdAt", q.notes AS notes
            FROM quotations q WHERE q.customer_id = :customerId
            ORDER BY "createdAt" DESC
            """, nativeQuery = true)
    List<com.novax.leadora.api.dto.response.CustomerHistoryProjection> findCustomerHistory(
            @Param("customerId") UUID customerId);
}
