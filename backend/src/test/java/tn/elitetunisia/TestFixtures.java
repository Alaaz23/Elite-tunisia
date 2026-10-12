package tn.elitetunisia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tn.elitetunisia.booking.BookingRequest;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.vehicle.VehicleRepository;

@Component
public class TestFixtures {
    @Autowired UserRepository users;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired VehicleRepository vehicles;
    @Autowired CustomerRepository customers;
    @Autowired BookingService bookingService;

    public Fixture createBooking(int hoursAhead) {
        String suffix = UUID.randomUUID().toString().substring(0,8);
        var user = users.saveAndFlush(new tn.elitetunisia.auth.entity.UserEntity(suffix + "@example.test", "not-a-login-password"));
        customers.saveAndFlush(new tn.elitetunisia.customer.CustomerEntity(user, "Test", "Client", "+21600000000"));
        var brand = brands.saveAndFlush(new tn.elitetunisia.brand.BrandEntity("B " + suffix, "b-" + suffix, "Test", null, true));
        var category = categories.saveAndFlush(new tn.elitetunisia.category.CategoryEntity("C " + suffix, "c-" + suffix, "Test", true));
        var vehicle = vehicles.saveAndFlush(new tn.elitetunisia.vehicle.VehicleEntity(brand, category, "Model", 2025, "Test", new BigDecimal("500"), new BigDecimal("3000"), new BigDecimal("700"), "TND", 5,4,tn.elitetunisia.vehicle.TransmissionType.AUTOMATIC, tn.elitetunisia.vehicle.FuelType.PETROL, true, tn.elitetunisia.vehicle.VehicleStatus.AVAILABLE));
        Instant start = Instant.now().plusSeconds(30 * 86400L).truncatedTo(ChronoUnit.SECONDS);
        var booking = bookingService.create(new BookingRequest(vehicle.getId(), start, start.plusSeconds(hoursAhead * 3600L), "Tunis", "Tunis", null), user.getId(), null);
        return new Fixture(user.getId(), booking, vehicle.getId());
    }

    public record Fixture(UUID owner, tn.elitetunisia.booking.BookingSummary booking, UUID vehicle) { }
}
