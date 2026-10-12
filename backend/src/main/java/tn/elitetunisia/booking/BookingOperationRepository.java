package tn.elitetunisia.booking;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingOperationRepository extends JpaRepository<BookingOperationEntity, UUID> {
    List<BookingOperationEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
