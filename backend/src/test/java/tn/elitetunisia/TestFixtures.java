package tn.elitetunisia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.booking.BookingRequest;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.partner.GuaranteeEntity;
import tn.elitetunisia.partner.GuaranteeRepository;
import tn.elitetunisia.partner.PartnerEntity;
import tn.elitetunisia.partner.PartnerRepository;
import tn.elitetunisia.partner.PartnerType;
import tn.elitetunisia.payment.PaymentMethod;
import tn.elitetunisia.payment.PaymentService;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;
import tn.elitetunisia.vehicle.VehicleRepository;

@Component
public class TestFixtures {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired VehicleRepository vehicles;
    @Autowired CustomerRepository customers;
    @Autowired PartnerRepository partners;
    @Autowired GuaranteeRepository guarantees;
    @Autowired BookingService bookingService;
    @Autowired QuoteService quoteService;
    @Autowired PaymentService paymentService;

    // Paiement intégral simulé (D-PA-10) : devis accepté par le client puis paiement CAPTURED du montant total.
    // Requis avant toute confirmation de réservation.
    public void payInFull(UUID bookingId, UUID ownerId) {
        var admin = new UserEntity(TestIdentities.email("fixture-admin"), "not-a-login-password");
        admin.addRole(roles.findById("SUPER_ADMIN").orElseThrow());
        UUID adminId = users.saveAndFlush(admin).getId();
        var quote = quoteService.create(bookingId, new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(ChronoUnit.SECONDS),
                false, BigDecimal.ZERO, "Proposition", "Internal only", List.of()), adminId, null);
        quoteService.send(quote.id(), adminId, null);
        quoteService.customerAction(quote.id(), ownerId, "accept", null, null);
        paymentService.createForQuote(quote.id(), ownerId, quote.totalAmount(), PaymentMethod.CARD, "pay-" + UUID.randomUUID());
    }

    public Fixture createBooking(int hoursAhead) {
        String suffix = UUID.randomUUID().toString().substring(0,8);
        var user = users.saveAndFlush(new tn.elitetunisia.auth.entity.UserEntity(TestIdentities.email("fixture-customer"), "not-a-login-password"));
        customers.saveAndFlush(new tn.elitetunisia.customer.CustomerEntity(user, "Test", "Client", "+21600000000"));
        var brand = brands.saveAndFlush(new tn.elitetunisia.brand.BrandEntity("B " + suffix, "b-" + suffix, "Test", null, true));
        var category = categories.saveAndFlush(new tn.elitetunisia.category.CategoryEntity("C " + suffix, "c-" + suffix, "Test", true));
        var vehicle = vehicles.saveAndFlush(new tn.elitetunisia.vehicle.VehicleEntity(brand, category, "Model", 2025, "Test", new BigDecimal("500"), new BigDecimal("3000"), new BigDecimal("700"), "TND", 5,4,tn.elitetunisia.vehicle.TransmissionType.AUTOMATIC, tn.elitetunisia.vehicle.FuelType.PETROL, true, tn.elitetunisia.vehicle.VehicleStatus.AVAILABLE));
        guaranteeVehicle(vehicle.getId(), user.getId());
        Instant start = Instant.now().plusSeconds(30 * 86400L).truncatedTo(ChronoUnit.SECONDS);
        var booking = bookingService.create(new BookingRequest(vehicle.getId(), start, start.plusSeconds(hoursAhead * 3600L), "Tunis", "Tunis", null), user.getId(), null);
        return new Fixture(user.getId(), booking, vehicle.getId());
    }

    // Garantie ACTIVE couvrant now-1j à now+60j : obligatoire pour réserver (lot 5). Une garantie par véhicule.
    public void guaranteeVehicle(UUID vehicleId, UUID actorId) {
        var vehicle = vehicles.findById(vehicleId).orElseThrow();
        var actor = users.findById(actorId).orElseThrow();
        var partner = partners.saveAndFlush(new PartnerEntity("Partner " + vehicleId.toString().substring(0, 8), PartnerType.COMPANY));
        Instant now = Instant.now();
        guarantees.saveAndFlush(new GuaranteeEntity(vehicle, partner, now.minusSeconds(86400L),
                now.plusSeconds(60L * 86400L), "fixture", actor, now));
    }


    public record Fixture(UUID owner, tn.elitetunisia.booking.BookingSummary booking, UUID vehicle) { }
}
