package tn.elitetunisia.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRequestHistoryRepository extends JpaRepository<BillingRequestHistoryEntity, Long> {
    List<BillingRequestHistoryEntity> findByRequestIdOrderByIdAsc(UUID requestId);
}
