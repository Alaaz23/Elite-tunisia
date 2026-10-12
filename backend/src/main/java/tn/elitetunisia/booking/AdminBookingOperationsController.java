package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasAuthority('bookings:manage')")
public class AdminBookingOperationsController {
    private final BookingOperationService ops;
    private final AccountService accounts;

    public AdminBookingOperationsController(BookingOperationService ops, AccountService accounts) { this.ops = ops; this.accounts = accounts; }

    public static record PickupRequest(@NotNull Integer odometerStart, @NotNull Integer fuelStart) { }
    public static record ReturnRequest(@NotNull Integer odometerEnd, @NotNull Integer fuelEnd, String damages, BigDecimal extraCharges) { }

    @PostMapping("/{id}/pickup")
    public BookingOperationEntity pickup(@PathVariable UUID id, @Valid @RequestBody PickupRequest request, Authentication auth, HttpServletRequest servlet) {
        UUID actor = accounts.current(auth.getName()).id();
        return ops.recordPickup(id, actor, request.odometerStart(), request.fuelStart());
    }

    @PostMapping("/{id}/return")
    public BookingOperationEntity ret(@PathVariable UUID id, @Valid @RequestBody ReturnRequest request, Authentication auth, HttpServletRequest servlet) {
        UUID actor = accounts.current(auth.getName()).id();
        return ops.recordReturn(id, actor, request.odometerEnd(), request.fuelEnd(), request.damages(), request.extraCharges());
    }
}
