package tn.elitetunisia.booking;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingEventRepository extends JpaRepository<BookingEventEntity, UUID> {
    List<BookingEventEntity> findByBookingIdOrderByCreatedAtAsc(UUID id);
}