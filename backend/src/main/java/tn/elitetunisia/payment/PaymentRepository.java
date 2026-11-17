package tn.elitetunisia.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByCustomerIdAndQuote_IdAndIdempotencyKey(UUID customerId, UUID quoteId, String idempotencyKey);
    long countByCustomerIdAndQuote_IdAndIdempotencyKey(UUID customerId, UUID quoteId, String idempotencyKey);
    boolean existsByQuote_IdAndStatusIn(UUID quoteId, Collection<PaymentStatus> statuses);
    boolean existsByBooking_IdAndStatusIn(UUID bookingId, Collection<PaymentStatus> statuses);

    @Query("select p.id from PaymentEntity p where p.status = tn.elitetunisia.payment.PaymentStatus.UNKNOWN "
            + "or (p.status = tn.elitetunisia.payment.PaymentStatus.PENDING and p.createdAt < :pendingBefore) order by p.createdAt")
    List<UUID> findReconciliationCandidates(@Param("pendingBefore") Instant pendingBefore, Pageable page);

    @Query("select p.booking.id from PaymentEntity p where p.id = :id")
    Optional<UUID> findBookingIdOf(@Param("id") UUID id);

    // Verrou bloquant, à n'utiliser qu'après le verrou Booking (ordre global Vehicle → Booking → Quote → Payment).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> findForUpdate(@Param("id") UUID id);

    // SKIP LOCKED : une réconciliation déjà en cours sur la même tentative est ignorée, jamais attendue.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> findForUpdateSkipLocked(@Param("id") UUID id);
}
