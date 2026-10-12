package tn.elitetunisia.quote;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuoteEventRepository extends JpaRepository<QuoteEventEntity, UUID> {
    List<QuoteEventEntity> findByQuoteIdOrderByCreatedAtAsc(UUID id);
}