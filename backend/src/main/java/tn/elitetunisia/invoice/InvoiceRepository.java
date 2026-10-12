package tn.elitetunisia.invoice;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, java.util.UUID> {
    Optional<InvoiceEntity> findByReference(String reference);
    List<InvoiceEntity> findByCustomerId(UUID customerId);
}
