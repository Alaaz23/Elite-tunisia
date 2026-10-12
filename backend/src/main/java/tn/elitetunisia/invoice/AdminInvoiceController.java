package tn.elitetunisia.invoice;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/invoices")
@PreAuthorize("hasAuthority('invoices:read')")
public class AdminInvoiceController {
    private final InvoiceService service; private final InvoiceDocumentService docs;
    public AdminInvoiceController(InvoiceService service, InvoiceDocumentService docs) { this.service = service; this.docs = docs; }
    @GetMapping public java.util.List<InvoiceAdminSummary> list() { return service.adminList().stream().map(InvoiceAdminSummary::from).toList(); }
    @GetMapping("/{id}") public InvoiceAdminDetail get(@PathVariable UUID id) { return InvoiceAdminDetail.from(service.adminGet(id)); }

    @GetMapping("/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var bytes = docs.generatePdf(id);
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        String filename = "invoice_" + id.toString() + ".pdf";
        headers.setContentDispositionFormData("inline", filename);
        return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
    }

    public record InvoiceAdminSummary(UUID id, String reference, java.math.BigDecimal totalAmount, String currency) { public static InvoiceAdminSummary from(InvoiceEntity e) { return new InvoiceAdminSummary(e.getId(), e.getReference(), e.getTotalAmount(), e.getCurrency()); } }
    public static class InvoiceAdminDetail { public UUID id; public String reference; public java.math.BigDecimal totalAmount; public String currency; public java.time.Instant issuedAt; public java.util.List<?> lines; public static InvoiceAdminDetail from(InvoiceEntity e) { var d = new InvoiceAdminDetail(); d.id = e.getId(); d.reference = e.getReference(); d.totalAmount = e.getTotalAmount(); d.currency = e.getCurrency(); d.issuedAt = e.getIssuedAt(); d.lines = e.getLines().stream().map(l -> java.util.Map.of("description", l.getDescription(), "amount", l.getAmount(), "qty", l.getQuantity())).toList(); return d; } }
}
