package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface BookingDriverAssignmentRepository extends JpaRepository<BookingDriverAssignmentEntity, UUID> {
    @Query("select count(a) from BookingDriverAssignmentEntity a where a.driverId = :driverId and a.startsAt < :end and a.endsAt > :start and (:excludedBookingId is null or a.bookingId <> :excludedBookingId)")
    long overlaps(UUID driverId, Instant start, Instant end, UUID excludedBookingId);

    BookingDriverAssignmentEntity findByBookingId(UUID bookingId);
}
