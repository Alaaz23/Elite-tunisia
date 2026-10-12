package tn.elitetunisia.invoice;

import java.util.UUID;
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

    public record InvoiceSummary(UUID id, String reference, java.math.BigDecimal totalAmount, String currency, InstantDTO issuedAt) {
        public static InvoiceSummary from(InvoiceEntity e) { return new InvoiceSummary(e.getId(), e.getReference(), e.getTotalAmount(), e.getCurrency(), new InstantDTO(e.getIssuedAt())); }
    }
    public static class InvoiceDetail {
        public UUID id; public String reference; public java.math.BigDecimal totalAmount; public String currency; public InstantDTO issuedAt; public java.util.List<?> lines;
        public static InvoiceDetail from(InvoiceEntity e) { var d = new InvoiceDetail(); d.id = e.getId(); d.reference = e.getReference(); d.totalAmount = e.getTotalAmount(); d.currency = e.getCurrency(); d.issuedAt = new InstantDTO(e.getIssuedAt()); d.lines = e.getLines().stream().map(l -> java.util.Map.of("description", l.getDescription(), "amount", l.getAmount())).toList(); return d; }
    }

    public record InstantDTO(java.time.Instant v) { }
}
