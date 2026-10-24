package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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

    @GetMapping("/{id}/operations")
    public List<OperationView> operations(@PathVariable UUID id) {
        return ops.list(id).stream().map(OperationView::from).toList();
    }

    public static record OperationView(UUID id, String action, Integer odometerStart, Integer odometerEnd,
            Integer fuelStart, Integer fuelEnd, String damages, BigDecimal extraCharges, java.time.Instant createdAt) {
        static OperationView from(BookingOperationEntity operation) {
            return new OperationView(operation.getId(), operation.getAction(), operation.getOdometerStart(),
                    operation.getOdometerEnd(), operation.getFuelStart(), operation.getFuelEnd(),
                    operation.getDamages(), operation.getExtraCharges(), operation.getCreatedAt());
        }
    }
    public static record PickupRequest(@NotNull @PositiveOrZero Integer odometerStart,
            @NotNull @Min(0) @Max(100) Integer fuelStart) { }
    public static record ReturnRequest(@NotNull @PositiveOrZero Integer odometerEnd,
            @NotNull @Min(0) @Max(100) Integer fuelEnd, @Size(max = 5000) String damages,
            @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal extraCharges) { }

    @PostMapping("/{id}/pickup")
    public OperationView pickup(@PathVariable UUID id, @Valid @RequestBody PickupRequest request, Authentication auth, HttpServletRequest servlet) {
        UUID actor = accounts.current(auth.getName()).id();
        return OperationView.from(ops.recordPickup(id, actor, request.odometerStart(), request.fuelStart()));
    }

    @PostMapping("/{id}/return")
    public OperationView ret(@PathVariable UUID id, @Valid @RequestBody ReturnRequest request, Authentication auth, HttpServletRequest servlet) {
        UUID actor = accounts.current(auth.getName()).id();
        return OperationView.from(ops.recordReturn(id, actor, request.odometerEnd(), request.fuelEnd(), request.damages(), request.extraCharges()));
    }
}
