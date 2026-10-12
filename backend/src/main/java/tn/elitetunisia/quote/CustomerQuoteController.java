package tn.elitetunisia.quote;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;
@RestController @RequestMapping("/api/customer/quotes") @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('quotes:read')")
public class CustomerQuoteController {
    private final QuoteService service; private final AccountService accounts;
    public CustomerQuoteController(QuoteService service, AccountService accounts) { this.service = service; this.accounts = accounts; }
    @GetMapping public PagedResponse<QuoteSummary> list(@ModelAttribute QuoteSearch filter, Authentication auth) { return service.list(filter, accounts.current(auth.getName()).id()); }
    @GetMapping("/{id}") public QuoteSummary get(@PathVariable UUID id, Authentication auth) { return service.get(id, accounts.current(auth.getName()).id()); }
    @PostMapping("/{id}/accept") @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('quotes:accept')")
    public QuoteSummary accept(@PathVariable UUID id, @Valid @RequestBody(required = false) QuoteMessage request, Authentication auth, HttpServletRequest servlet) { return action(id, "accept", request, auth, servlet); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('quotes:reject')")
    public QuoteSummary reject(@PathVariable UUID id, @Valid @RequestBody(required = false) QuoteMessage request, Authentication auth, HttpServletRequest servlet) { return action(id, "reject", request, auth, servlet); }
    @PostMapping("/{id}/request-change") @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('quotes:read')")
    public QuoteSummary requestChange(@PathVariable UUID id, @Valid @RequestBody(required = false) QuoteMessage request, Authentication auth, HttpServletRequest servlet) { return action(id, "request-change", request, auth, servlet); }
    private QuoteSummary action(UUID id, String action, QuoteMessage request, Authentication auth, HttpServletRequest servlet) { return service.customerAction(id, accounts.current(auth.getName()).id(), action, request == null ? null : request.message(), servlet.getRemoteAddr()); }
}