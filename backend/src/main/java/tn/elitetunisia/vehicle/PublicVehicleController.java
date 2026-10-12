package tn.elitetunisia.vehicle;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.catalogue.dto.VehicleSearchRequest;
import tn.elitetunisia.catalogue.dto.VehicleSummary;

@RestController
@RequestMapping("/api/vehicles")
public class PublicVehicleController {
    private final VehicleService vehicles;
    public PublicVehicleController(VehicleService vehicles) { this.vehicles = vehicles; }

    @GetMapping
    public PagedResponse<VehicleSummary> search(@ModelAttribute VehicleSearchRequest filter) { return vehicles.publicSearch(filter); }

    @GetMapping("/{id}")
    public VehicleSummary get(@PathVariable UUID id) { return vehicles.publicGet(id); }
    @GetMapping("/available")
    public PagedResponse<VehicleSummary> available(@ModelAttribute VehicleSearchRequest filter,
            @org.springframework.web.bind.annotation.RequestParam java.time.Instant startDateTime,
            @org.springframework.web.bind.annotation.RequestParam java.time.Instant endDateTime) {
        if (startDateTime == null || endDateTime == null || !endDateTime.isAfter(startDateTime) || !startDateTime.isAfter(java.time.Instant.now())
            || startDateTime.getNano() % 1000 != 0 || endDateTime.getNano() % 1000 != 0) {
            throw new tn.elitetunisia.booking.BookingException("INVALID_DATE_RANGE", "La période doit être valide et future.");
        }
        return vehicles.availableSearch(filter, startDateTime, endDateTime);
    }
}