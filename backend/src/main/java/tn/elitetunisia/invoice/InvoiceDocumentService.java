package tn.elitetunisia.invoice;

import java.util.UUID;

public interface InvoiceDocumentService {
    /**
     * Generate a PDF document for the invoice. Not implemented in this iteration.
     * @return raw PDF bytes
     */
    byte[] generatePdf(UUID invoiceId);
}
