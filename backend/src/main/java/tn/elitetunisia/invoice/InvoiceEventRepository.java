package tn.elitetunisia.invoice;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceEventRepository extends JpaRepository<InvoiceEventEntity, java.util.UUID> {
    List<InvoiceEventEntity> findByInvoiceId(UUID invoiceId);
}
