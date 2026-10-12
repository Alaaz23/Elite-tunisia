package tn.elitetunisia.invoice;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

@Service
public class InvoiceDocumentServiceImpl implements InvoiceDocumentService {
    private final InvoiceRepository invoices;

    public InvoiceDocumentServiceImpl(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public byte[] generatePdf(UUID invoiceId) {
        var inv = invoices.findById(invoiceId).orElseThrow(() -> new RuntimeException("INVOICE_NOT_FOUND"));
        return generatePdfFromEntity(inv);
    }

    // Exposed for tests to avoid requiring a real repository implementation
    public byte[] generatePdfFromEntity(InvoiceEntity inv) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Invoice: " + inv.getReference()));
            doc.add(new Paragraph("Issued: " + inv.getIssuedAt()));
            doc.add(new Paragraph("Customer: " + inv.getCustomerId()));
            doc.add(new Paragraph("Total: " + inv.getTotalAmount() + " " + inv.getCurrency()));
            doc.add(new Paragraph("\nLines:"));
            for (var line : inv.getLines()) {
                doc.add(new Paragraph(String.format("- %s x%d: %s %s", line.getDescription(), line.getQuantity(), line.getUnitPrice(), inv.getCurrency())));
            }
            doc.close();
            return out.toByteArray();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("PDF_GENERATION_FAILED", ex);
        }
    }
}
