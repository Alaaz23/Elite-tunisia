package tn.elitetunisia.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/quotes/{quoteId}/payments")
public class CustomerPaymentController {
    private final PaymentService service;
    private final tn.elitetunisia.user.service.AccountService accounts;

    public CustomerPaymentController(PaymentService service, tn.elitetunisia.user.service.AccountService accounts) { this.service = service; this.accounts = accounts; }

    public static record PaymentRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal amount,
            @jakarta.validation.constraints.NotNull PaymentMethod method,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String idempotencyKey) {}
    public static record PaymentSummary(String id, String status, String transactionRef, BigDecimal amount) {}

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('payments:create')")
    public ResponseEntity<PaymentSummary> create(@PathVariable("quoteId") UUID quoteId, @jakarta.validation.Valid @RequestBody PaymentRequest request,
            org.springframework.security.core.Authentication auth) {
        var payment = service.createForQuote(quoteId, accounts.current(auth.getName()).id(), request.amount(), request.method(), request.idempotencyKey());
        return ResponseEntity.ok(new PaymentSummary(payment.getId().toString(), payment.getStatus().name(), payment.getTransactionRef(), payment.getAmount()));
    }
}
