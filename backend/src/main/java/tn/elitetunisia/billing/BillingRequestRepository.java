package tn.elitetunisia.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRequestRepository extends JpaRepository<BillingRequestEntity, UUID> {
    Optional<BillingRequestEntity> findByBookingIdAndKind(UUID bookingId, String kind);

    List<BillingRequestEntity> findTop100ByStatusOrderByCreatedAtAsc(BillingRequestEntity.Status status);

    Page<BillingRequestEntity> findByStatus(BillingRequestEntity.Status status, Pageable pageable);

    List<BillingRequestEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            BillingRequestEntity.Status status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BillingRequestEntity r where r.id = :id")
    Optional<BillingRequestEntity> findLockedById(@Param("id") UUID id);
}
