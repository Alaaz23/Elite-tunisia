package tn.elitetunisia.booking;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;
@RestController @RequestMapping("/api/bookings")
@PreAuthorize("hasRole('CUSTOMER') and hasAuthority('bookings:read')")
public class CustomerBookingController {
    private final BookingService service;
    private final AccountService accounts;
    public CustomerBookingController(BookingService service, AccountService accounts) { this.service = service; this.accounts = accounts; }
    @GetMapping public PagedResponse<BookingSummary> list(@ModelAttribute BookingSearch filter, Authentication auth) {
        return service.list(filter, accounts.current(auth.getName()).id());
    }
    @GetMapping("/{id}") public BookingSummary get(@PathVariable UUID id, Authentication auth) {
        return service.customerGet(id, accounts.current(auth.getName()).id());
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('bookings:create')")
    public BookingSummary create(@Valid @RequestBody BookingRequest request, Authentication auth, HttpServletRequest servlet) {
        return service.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('bookings:cancel')")
    public BookingSummary cancel(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return service.customerCancel(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}