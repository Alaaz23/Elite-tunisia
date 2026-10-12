package tn.elitetunisia.booking;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;
@RestController @RequestMapping("/api/admin/bookings")
@PreAuthorize("hasAuthority('bookings:read')")
public class AdminBookingController {
    private final BookingService service;
    private final AccountService accounts;
    public AdminBookingController(BookingService service, AccountService accounts) { this.service = service; this.accounts = accounts; }
    @GetMapping public PagedResponse<BookingSummary> list(@ModelAttribute BookingSearch filter) { return service.list(filter, null); }
    @GetMapping("/{id}") public BookingSummary get(@PathVariable UUID id) { return service.adminGet(id); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('bookings:manage')")
    public BookingSummary update(@PathVariable UUID id, @Valid @RequestBody BookingUpdateRequest request, Authentication auth, HttpServletRequest servlet) {
        return service.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
    @PatchMapping("/{id}/confirm") @PreAuthorize("hasAuthority('bookings:manage')")
    public BookingSummary confirm(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return change(id, BookingStatus.CONFIRMED, auth, servlet); }
    @PatchMapping("/{id}/reject") @PreAuthorize("hasAuthority('bookings:manage')")
    public BookingSummary reject(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return change(id, BookingStatus.REJECTED, auth, servlet); }
    @PatchMapping("/{id}/cancel") @PreAuthorize("hasAuthority('bookings:cancel')")
    public BookingSummary cancel(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return change(id, BookingStatus.CANCELLED, auth, servlet); }
    @PatchMapping("/{id}/start") @PreAuthorize("hasAuthority('bookings:manage')")
    public BookingSummary start(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return change(id, BookingStatus.IN_PROGRESS, auth, servlet); }
    @PatchMapping("/{id}/complete") @PreAuthorize("hasAuthority('bookings:manage')")
    public BookingSummary complete(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return change(id, BookingStatus.COMPLETED, auth, servlet); }
    private BookingSummary change(UUID id, BookingStatus target, Authentication auth, HttpServletRequest servlet) {
        return service.transition(id, target, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}