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

    // Seules les réservations CONFIRMED et IN_PROGRESS bloquent le véhicule ; une demande PENDING ne bloque jamais.
    @Query("select count(booking) from BookingEntity booking where booking.vehicle.id = :vehicleId and booking.startDateTime < :end and booking.endDateTime > :start"
            + " and booking.status in (tn.elitetunisia.booking.BookingStatus.CONFIRMED, tn.elitetunisia.booking.BookingStatus.IN_PROGRESS)"
            + " and (:excludedId is null or booking.id <> :excludedId)")
    long countBlocking(UUID vehicleId, Instant start, Instant end, UUID excludedId);

    @Query("select count(booking) from BookingEntity booking where booking.vehicle.id = :vehicleId and booking.status in :statuses")
    long countByVehicle(UUID vehicleId, List<BookingStatus> statuses);

    @Query("select count(booking) from BookingEntity booking where booking.vehicle.id = :vehicleId and booking.status = :status and booking.endDateTime > :after")
    long countByVehicleEndingAfter(UUID vehicleId, BookingStatus status, Instant after);
}