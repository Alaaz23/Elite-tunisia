package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface BookingRepository extends JpaRepository<BookingEntity, UUID>, JpaSpecificationExecutor<BookingEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select booking from BookingEntity booking where booking.id = :id")
    Optional<BookingEntity> findLocked(UUID id);
    Optional<BookingEntity> findByIdAndCustomerId(UUID id, UUID customerId);
    @Query("select count(booking) from BookingEntity booking where booking.vehicle.id = :vehicleId and booking.status in :statuses and booking.startDateTime < :end and booking.endDateTime > :start and (:excludedId is null or booking.id <> :excludedId)")
    long overlaps(UUID vehicleId, Instant start, Instant end, List<BookingStatus> statuses, UUID excludedId);
}