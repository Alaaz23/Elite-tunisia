package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class BookingPublicController {
    private final BookingService service;
    public BookingPublicController(BookingService service) { this.service = service; }
    @GetMapping("/vehicles/{vehicleId}/availability")
    public AvailabilityResponse availability(@PathVariable UUID vehicleId, @RequestParam Instant startDateTime, @RequestParam Instant endDateTime) {
        return service.availability(vehicleId, startDateTime, endDateTime);
    }
    @PostMapping("/bookings/quote-preview")
    public BookingPreview preview(@Valid @RequestBody BookingRequest request) { return service.preview(request); }
    @GetMapping("/bookings/policy")
    public Map<String, Object> policy() { return Map.of("businessTimeZone", service.timeZone(), "pendingBlocks", false, "priceRule", "STARTED_24_HOURS", "options", List.of()); }
}