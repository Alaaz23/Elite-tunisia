package tn.elitetunisia.invoice;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasRole('CUSTOMER') and hasAuthority('invoices:read')")
public class CustomerInvoiceController {
    private final InvoiceService service; private final AccountService accounts; private final InvoiceDocumentService docs;
    public CustomerInvoiceController(InvoiceService service, AccountService accounts, InvoiceDocumentService docs) { this.service = service; this.accounts = accounts; this.docs = docs; }
    @GetMapping public java.util.List<InvoiceSummary> list(Authentication auth) {
        var id = accounts.current(auth.getName()).id();
        return service.listForCustomer(id).stream().map(InvoiceSummary::from).toList();
    }
    @GetMapping("/{id}") public InvoiceDetail get(@PathVariable UUID id, Authentication auth) {
        var customerId = accounts.current(auth.getName()).id();
        var inv = service.getForCustomer(id, customerId);
        return InvoiceDetail.from(inv);
    }

    @GetMapping("/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable UUID id, Authentication auth) {
        var customerId = accounts.current(auth.getName()).id();
        var inv = service.getForCustomer(id, customerId);
        var bytes = docs.generatePdf(inv.getId());
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        String filename = inv.getReference().replaceAll("[^A-Za-z0-9_.-]", "_") + ".pdf";
        headers.setContentDispositionFormData("inline", filename);
        return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
    }

    public record InvoiceSummary(UUID id, String reference, java.math.BigDecimal totalAmount, String currency, java.time.Instant issuedAt) {
        public static InvoiceSummary from(InvoiceEntity e) { return new InvoiceSummary(e.getId(), e.getReference(), e.getTotalAmount(), e.getCurrency(), e.getIssuedAt()); }
    }
    public record InvoiceDetail(UUID id, String reference, java.math.BigDecimal totalAmount, String currency,
            java.time.Instant issuedAt, List<InvoiceLine> lines) {
        public static InvoiceDetail from(InvoiceEntity e) {
            List<InvoiceLine> lines = e.getLines().stream()
                    .map(line -> new InvoiceLine(line.getId(), line.getType(), line.getDescription(), line.getQuantity(),
                            line.getUnitPrice(), line.getAmount(), line.getPosition(), line.getMetadata()))
                    .toList();
            return new InvoiceDetail(e.getId(), e.getReference(), e.getTotalAmount(), e.getCurrency(), e.getIssuedAt(), lines);
        }
    }

    public record InvoiceLine(UUID id, String type, String description, int quantity, java.math.BigDecimal unitPrice,
            java.math.BigDecimal amount, int position, Map<String, String> metadata) { }
}
