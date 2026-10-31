package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {
    Page<TransferEntity> findByBookingId(UUID bookingId, Pageable pageable);
    Page<TransferEntity> findByStatus(TransferStatus status, Pageable pageable);
    Page<TransferEntity> findByBookingIdAndStatus(UUID bookingId, TransferStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferEntity t where t.id = :id")
    Optional<TransferEntity> findLockedById(UUID id);

    /** Transferts actifs du même chauffeur dont la prise en charge tombe dans la fenêtre ]from, to[. */
    @Query("select count(t) from TransferEntity t where t.driverId = :driverId and t.id <> :excludedId "
            + "and t.status in (tn.elitetunisia.transfer.TransferStatus.PENDING, tn.elitetunisia.transfer.TransferStatus.CONFIRMED) "
            + "and t.pickupAt > :from and t.pickupAt < :to")
    long countActiveForDriverBetween(UUID driverId, UUID excludedId, Instant from, Instant to);
}
