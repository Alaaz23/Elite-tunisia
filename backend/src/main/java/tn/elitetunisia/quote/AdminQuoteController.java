package tn.elitetunisia.quote;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;
@RestController @RequestMapping("/api/admin/quotes") @PreAuthorize("hasAuthority('quotes:read')")
public class AdminQuoteController {
    private final QuoteService service; private final AccountService accounts;
    public AdminQuoteController(QuoteService service, AccountService accounts) { this.service = service; this.accounts = accounts; }
    @GetMapping public PagedResponse<QuoteSummary> list(@ModelAttribute QuoteSearch filter) { return service.list(filter, null); }
    @GetMapping("/{id}") public QuoteSummary get(@PathVariable UUID id) { return service.get(id, null); }
    @GetMapping("/options/{bookingId}") public Map<String,Object> options(@PathVariable UUID bookingId) { return service.options(bookingId); }
    @PostMapping("/booking/{bookingId}") @PreAuthorize("hasAuthority('quotes:create') and hasAuthority('quotes:manage')")
    public QuoteSummary create(@PathVariable UUID bookingId, @Valid @RequestBody QuoteRequest request, Authentication auth, HttpServletRequest servlet) { return service.create(bookingId, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr()); }
    @PostMapping("/{id}/versions") @PreAuthorize("hasAuthority('quotes:create') and hasAuthority('quotes:manage')")
    public QuoteSummary version(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request, Authentication auth, HttpServletRequest servlet) { return service.create(service.get(id, null).bookingId(), request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr()); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('quotes:manage')")
    public QuoteSummary update(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request, Authentication auth, HttpServletRequest servlet) { return service.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr()); }
    @PostMapping("/{id}/send") @PreAuthorize("hasAuthority('quotes:send')")
    public QuoteSummary send(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return service.send(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr()); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('quotes:manage')")
    public QuoteSummary cancel(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return service.cancel(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr()); }
}