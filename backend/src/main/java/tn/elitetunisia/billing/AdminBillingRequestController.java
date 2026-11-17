package tn.elitetunisia.billing;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.common.web.AdminPageRequest;
import tn.elitetunisia.user.service.AccountService;

/** Supervision de l'outbox de facturation : demandes DEAD, historique et reprise administrative auditée. */
@RestController
@RequestMapping("/api/admin/billing/requests")
@PreAuthorize("hasAuthority('billing:backfill')")
public class AdminBillingRequestController {
    private static final Set<String> SORTABLE = Set.of("createdAt", "nextAttemptAt", "attempts");
    public record RequestView(UUID id, UUID bookingId, String kind, String status, int attempts, int cycleAttempts,
            Instant nextAttemptAt, String lastError, UUID invoiceId) { }
    public record RetryRequest(String reason) { }

    private final BillingRequestService billing;
    private final AccountService accounts;

    public AdminBillingRequestController(BillingRequestService billing, AccountService accounts) {
        this.billing = billing; this.accounts = accounts;
    }

    @GetMapping
    public PagedResponse<RequestView> list(@RequestParam(defaultValue = "DEAD") BillingRequestEntity.Status status,
            Pageable pageable) {
        Page<RequestView> page = billing.listByStatus(status, AdminPageRequest.of(pageable, SORTABLE, "createdAt",
                Sort.Direction.ASC)).map(AdminBillingRequestController::view);
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @GetMapping("/{id}/history")
    public List<BillingRequestService.HistoryLine> history(@PathVariable UUID id) {
        return billing.history(id);
    }

    @PostMapping("/{id}/retry")
    public RequestView retry(@PathVariable UUID id, @RequestBody RetryRequest body, Authentication auth) {
        var actorId = accounts.current(auth.getName()).id();
        return view(billing.retryDeadRequest(id, actorId, body == null ? null : body.reason()));
    }

    private static RequestView view(BillingRequestEntity r) {
        return new RequestView(r.getId(), r.getBookingId(), r.getKind(), r.getStatus().name(), r.getAttempts(),
                r.getCycleAttempts(), r.getNextAttemptAt(), r.getLastError(), r.getInvoiceId());
    }
}
