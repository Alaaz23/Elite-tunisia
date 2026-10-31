package tn.elitetunisia.invoice;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class InvoiceDocumentServiceTest {
    @Test
    public void generatesPdfBytesAndContainsHeader() {
        UUID customer = UUID.randomUUID();
        InvoiceEntity inv = new InvoiceEntity("REF-TEST", null, null, customer, "TND", new BigDecimal("100.000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.000"), Instant.now(), null);
        inv.addLine(new InvoiceLineEntity("VEHICLE_RENTAL", "Location véhicule", 1, new BigDecimal("100.000"), java.util.Map.of(), 0));

        InvoiceDocumentServiceImpl svc = new InvoiceDocumentServiceImpl(null);
        byte[] pdf = svc.generatePdfFromEntity(inv);
        assertNotNull(pdf);
        assertTrue(pdf.length > 10);
        String header = new String(pdf, 0, Math.min(4, pdf.length));
        assertTrue(header.contains("%PDF"));
    }

    @Test
    public void missingInvoiceIsReportedAsNotFound() {
        var repository = org.mockito.Mockito.mock(InvoiceRepository.class);
        org.mockito.Mockito.when(repository.findWithLinesById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        InvoiceDocumentServiceImpl svc = new InvoiceDocumentServiceImpl(repository);
        var ex = assertThrows(tn.elitetunisia.common.exception.DomainException.class, () -> svc.generatePdf(UUID.randomUUID()));
        assertEquals("INVOICE_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }
}
