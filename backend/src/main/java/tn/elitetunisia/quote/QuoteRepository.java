package tn.elitetunisia.quote;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID>, JpaSpecificationExecutor<QuoteEntity> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from QuoteEntity quote where quote.id = :id")
    Optional<QuoteEntity> findLockedById(UUID id);
    Optional<QuoteEntity> findFirstByBookingIdOrderByVersionDesc(UUID bookingId);
    Optional<QuoteEntity> findByIdAndCustomerId(UUID id, UUID customerId);
    @Query("select quote.id from QuoteEntity quote where quote.status = :status and quote.validUntil <= :now")
    List<UUID> expiredIds(QuoteStatus status, Instant now, org.springframework.data.domain.Pageable pageable);
}