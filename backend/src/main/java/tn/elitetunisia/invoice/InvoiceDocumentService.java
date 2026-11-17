package tn.elitetunisia.invoice;

import java.util.UUID;

public interface InvoiceDocumentService {
    /**
     * Generate a PDF document for the invoice.
     * @return raw PDF bytes
     */
    byte[] generatePdf(UUID invoiceId);
}
