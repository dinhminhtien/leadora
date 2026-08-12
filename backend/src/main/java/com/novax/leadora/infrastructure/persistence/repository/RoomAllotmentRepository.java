package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.RoomAllotmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Date ranges here are always <b>[from, toExclusive)</b>, matching how a stay occupies nights:
 * a 20→22 booking holds the nights of the 20th and 21st, not the 22nd. Callers that mean an
 * inclusive calendar range (the grid screen) add a day themselves rather than having two
 * conventions live in one repository.
 */
@Repository
public interface RoomAllotmentRepository extends JpaRepository<RoomAllotmentEntity, UUID> {

    /** Published quota for a set of room types over a range — the grid and the stay lookup. */
    @Query("""
            SELECT a FROM RoomAllotmentEntity a
            WHERE a.product.productId IN :productIds
              AND a.stayDate >= :from AND a.stayDate < :toExclusive
            ORDER BY a.product.productId ASC, a.stayDate ASC
            """)
    List<RoomAllotmentEntity> findPublished(
            @Param("productIds") Collection<UUID> productIds,
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);

    /**
     * Locked read of every night a sale would consume, used before deducting quota.
     *
     * <p>An {@code @Version} column alone cannot prevent overselling here: creating a hold only
     * <em>reads</em> the allotment and then writes to a different table, so two transactions can
     * both read "1 left", both find themselves valid, and both commit. The rows have to be locked
     * for the duration of the check-and-write.
     *
     * <p><b>{@code ORDER BY} is load-bearing, not cosmetic.</b> Two overlapping stays must take
     * their row locks in the same sequence or they deadlock — one holding the 10th while waiting
     * on the 11th, the other the reverse. Product then date is a cheap total order; every caller
     * that locks allotment rows must use this method rather than rolling its own query.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM RoomAllotmentEntity a
            WHERE a.product.productId IN :productIds
              AND a.stayDate >= :from AND a.stayDate < :toExclusive
            ORDER BY a.product.productId ASC, a.stayDate ASC
            """)
    List<RoomAllotmentEntity> lockNightsForUpdate(
            @Param("productIds") Collection<UUID> productIds,
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);

    /**
     * Publish one night's quota, inserting or overwriting in a single statement.
     *
     * <p>Read-then-write would need a lock to be safe against a second publisher, and the natural
     * key already carries a unique constraint, so {@code ON CONFLICT} lets the database settle it.
     * Native SQL because JPQL has no upsert.
     *
     * <p>{@code version_lock} is left alone on update: the row's optimistic version belongs to
     * entity-managed edits, and bumping it from raw SQL would silently invalidate any in-flight
     * managed copy.
     */
    @Modifying
    @Query(value = """
            INSERT INTO room_allotments (
                allotment_id, product_id, stay_date, allotted_qty, closed,
                note, as_of, updated_by, version_lock, created_at, updated_at)
            VALUES (
                gen_random_uuid(), :productId, :stayDate, :allottedQty, :closed,
                :note, :asOf, :updatedBy, 0, now(), now())
            ON CONFLICT (product_id, stay_date) DO UPDATE SET
                allotted_qty = EXCLUDED.allotted_qty,
                closed       = EXCLUDED.closed,
                note         = EXCLUDED.note,
                as_of        = EXCLUDED.as_of,
                updated_by   = EXCLUDED.updated_by,
                updated_at   = now()
            """, nativeQuery = true)
    void upsertNight(
            @Param("productId") UUID productId,
            @Param("stayDate") LocalDate stayDate,
            @Param("allottedQty") int allottedQty,
            @Param("closed") boolean closed,
            @Param("note") String note,
            @Param("asOf") OffsetDateTime asOf,
            @Param("updatedBy") UUID updatedBy);
}
