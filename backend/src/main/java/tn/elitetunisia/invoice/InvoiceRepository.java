package tn.elitetunisia.invoice;

import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, java.util.UUID> {
    Optional<InvoiceEntity> findByReference(String reference);
    List<InvoiceEntity> findByCustomerId(UUID customerId);

    /** Charge les lignes avec la facture : open-in-view est désactivé, une collection paresseuse n'est plus lisible dans le contrôleur. */
    @EntityGraph(attributePaths = "lines")
    Optional<InvoiceEntity> findWithLinesById(UUID id);
}
