package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.*;
import tn.elitetunisia.brand.*;
import tn.elitetunisia.category.*;
import tn.elitetunisia.customer.*;
import tn.elitetunisia.vehicle.*;
import tn.elitetunisia.booking.*;
import tn.elitetunisia.catalogue.dto.VehicleSearchRequest;
import tn.elitetunisia.payment.PaymentMethod;
import tn.elitetunisia.payment.PaymentService;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;

@SpringBootTest
@AutoConfigureMockMvc
class BookingEngineTests {
    static {
        if (!PostgresTestHelper.configurePostgresIfRequested()) {
            System.setProperty("spring.profiles.active", "test");
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:elite_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            System.setProperty("spring.flyway.enabled", "false");
            System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        }
    }
    @Autowired BookingService service;
    @Autowired BookingOperationService operations;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CustomerRepository customers;
    @Autowired VehicleRepository vehicles;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quoteService;
    @Autowired PaymentService paymentService;

    @Test void datesAndStarted24HourPricing() {
        Fixture fixture = fixture();
        assertEquals(1, service.preview(fixture.request()).numberOfDays());
        BookingRequest longer = request(fixture, fixture.start(), fixture.end().plusSeconds(1));
        assertEquals(2, service.preview(longer).numberOfDays());
        assertEquals(new BigDecimal("200.246"), service.preview(longer).basePrice());
        for (BookingRequest invalid : List.of(request(fixture, fixture.start(), fixture.start()),
                request(fixture, fixture.end(), fixture.start()), request(fixture, Instant.now().minusSeconds(1), fixture.end()))) {
            assertEquals("INVALID_DATE_RANGE", assertThrows(BookingException.class, () -> service.preview(invalid)).getCode());
        }
        assertEquals("INVALID_DATE_RANGE", assertThrows(BookingException.class, () -> service.validateDates(null, fixture.end())).getCode());
        assertEquals("INVALID_DATE_RANGE", assertThrows(BookingException.class, () -> service.preview(request(fixture, fixture.start(), fixture.end().plusNanos(1)))).getCode());
    }
    @Test void pendingRequestDoesNotBlockDatesButConfirmedIntervalsAreHalfOpen() {
        Fixture fixture = fixture();
        BookingSummary created = service.create(fixture.request(), fixture.owner(), null);
        assertEquals(BookingStatus.PENDING, created.status());
        assertTrue(service.availability(fixture.vehicle(), fixture.start(), fixture.end()).available());
        payInFull(created.id(), fixture);
        service.transition(created.id(), BookingStatus.CONFIRMED, admin(), null);
        assertFalse(service.availability(fixture.vehicle(), fixture.start(), fixture.end()).available());
        assertFalse(service.availability(fixture.vehicle(), fixture.start().plusSeconds(1), fixture.end().plusSeconds(1)).available());
        assertTrue(service.availability(fixture.vehicle(), fixture.start().minusSeconds(86400), fixture.start()).available());
        assertTrue(service.availability(fixture.vehicle(), fixture.end(), fixture.end().plusSeconds(86400)).available());
        assertEquals("VEHICLE_ALREADY_BOOKED", service.availability(fixture.vehicle(), fixture.start(), fixture.end()).reason());
    }
    @Test void createIgnoresForgedOwnerStatusReferenceAndAmount() throws Exception {
        Fixture fixture = fixture();
        mvc.perform(get("/api/vehicles/{id}/availability", fixture.vehicle())).andExpect(status().isBadRequest());
        Map<String, Object> body = new HashMap<>(body(fixture));
        body.put("customerId", UUID.randomUUID()); body.put("status", "CONFIRMED"); body.put("reference", "FORGED"); body.put("totalAmount", 1);
        mvc.perform(post("/api/bookings").with(customer(fixture)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(fixture.owner().toString()))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.totalAmount").value(100.123))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("ET-[0-9]{4}-[0-9]{6,}")));
        mvc.perform(post("/api/bookings/quote-preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":\"" + fixture.vehicle() + "\",\"startDateTime\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void customerIsolationAndPermissionsAreServerSide() throws Exception {
        Fixture owner = fixture(); Fixture other = fixture();
        BookingSummary booking = service.create(owner.request(), owner.owner(), null);
        mvc.perform(get("/api/bookings/{id}", booking.id()).with(customer(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/{id}/cancel", booking.id()).with(customer(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/bookings/{id}/confirm", booking.id()).with(customer(owner)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings").with(customer(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings").with(customer(other))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void agencyTransitionsCancellationHistoryAndNotes() {
        Fixture fixture = fixture();
        BookingSummary booking = service.create(fixture.request(), fixture.owner(), null);
        var shifted = request(fixture, fixture.start().plusSeconds(5 * 86400L), fixture.end().plusSeconds(5 * 86400L));
        assertNotEquals(booking.reference(), service.create(shifted, fixture.owner(), null).reference());
        service.update(booking.id(), new BookingUpdateRequest("Tunis", "Sousse", "Internal only"), admin(), null);
        assertNull(service.customerGet(booking.id(), fixture.owner()).internalNote());
        assertEquals("Internal only", service.adminGet(booking.id()).internalNote());
        payInFull(booking.id(), fixture);
        BookingSummary confirmed = service.transition(booking.id(), BookingStatus.CONFIRMED, admin(), null);
        assertNotNull(confirmed.confirmedAt());
        assertEquals("INVALID_OPERATION", assertThrows(BookingException.class,
                () -> service.transition(booking.id(), BookingStatus.IN_PROGRESS, admin(), null)).getCode());
        operations.recordPickup(booking.id(), admin(), 1000, 80);
        service.transition(booking.id(), BookingStatus.IN_PROGRESS, admin(), null);
        assertEquals("INVALID_OPERATION", assertThrows(BookingException.class,
                () -> service.transition(booking.id(), BookingStatus.COMPLETED, admin(), null)).getCode());
        operations.recordReturn(booking.id(), admin(), 1120, 65, null, new BigDecimal("35.500"));
        service.transition(booking.id(), BookingStatus.COMPLETED, admin(), null);
        assertThrows(BookingException.class, () -> service.transition(booking.id(), BookingStatus.PENDING, admin(), null));
        BookingSummary pending = service.create(fixture.request(), fixture.owner(), null);
        BookingSummary cancelled = service.customerCancel(pending.id(), fixture.owner(), null);
        assertNotNull(cancelled.cancelledAt());
        assertEquals("BOOKING_ALREADY_CANCELLED", assertThrows(BookingException.class, () -> service.customerCancel(pending.id(), fixture.owner(), null)).getCode());
        assertTrue(service.adminGet(booking.id()).history().size() >= 5);
        var rejected = service.create(fixture.request(), fixture.owner(), null);
        service.transition(rejected.id(), BookingStatus.REJECTED, admin(), null);
        assertEquals("INVALID_STATUS_TRANSITION", assertThrows(BookingException.class, () -> service.transition(rejected.id(), BookingStatus.IN_PROGRESS, admin(), null)).getCode());
        assertTrue(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action = 'BOOKING_CONFIRMED'", Integer.class, booking.id()) > 0);
    }
    @Test void confirmationRequiresFullPaymentOfAcceptedQuote() {
        Fixture fixture = fixture();
        BookingSummary booking = service.create(fixture.request(), fixture.owner(), null);
        assertEquals("PAYMENT_REQUIRED", assertThrows(BookingException.class,
                () -> service.transition(booking.id(), BookingStatus.CONFIRMED, admin(), null)).getCode());
        assertEquals(BookingStatus.PENDING, service.adminGet(booking.id()).status());
        payInFull(booking.id(), fixture);
        assertEquals(BookingStatus.CONFIRMED, service.transition(booking.id(), BookingStatus.CONFIRMED, admin(), null).status());
    }
    @Test void administrativeStatusAndAvailableCatalogueFilters() throws Exception {
        Fixture fixture = fixture();
        var vehicle = vehicles.findById(fixture.vehicle()).orElseThrow();
        vehicle.setStatus(VehicleStatus.MAINTENANCE); vehicles.saveAndFlush(vehicle);
        assertFalse(service.availability(fixture.vehicle(), fixture.start(), fixture.end()).available());
        assertThrows(BookingException.class, () -> service.create(fixture.request(), fixture.owner(), null));
        vehicle.setStatus(VehicleStatus.AVAILABLE); vehicles.saveAndFlush(vehicle);
        String brand = brands.findById(vehicle.getBrand().getId()).orElseThrow().getSlug();
        mvc.perform(get("/api/vehicles/available").param("startDateTime", fixture.start().toString()).param("endDateTime", fixture.end().toString())
                .param("brand", brand).param("passengers", "4").param("sort", "price_asc").param("size", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        var booking = service.create(fixture.request(), fixture.owner(), null);
        payInFull(booking.id(), fixture);
        service.transition(booking.id(), BookingStatus.CONFIRMED, admin(), null);
        mvc.perform(get("/api/vehicles/available").param("startDateTime", fixture.start().toString()).param("endDateTime", fixture.end().toString()).param("brand", brand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test @EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
    void concurrentPendingCreationsOnSamePeriodDoNotBlockFleetOnPostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        Fixture fixture = fixture();
        List<Boolean> outcomes = race(() -> tryCreate(fixture), () -> tryCreate(fixture));
        assertEquals(2, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(2, bookings.overlaps(fixture.vehicle(), fixture.start(), fixture.end(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS), null));
    }
    private void payInFull(UUID bookingId, Fixture fixture) {
        var quote = quoteService.create(bookingId, new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                false, BigDecimal.ZERO, "Proposition", "Internal only", List.of()), admin(), null);
        quoteService.send(quote.id(), admin(), null);
        quoteService.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        paymentService.createForQuote(quote.id(), fixture.owner(), quote.totalAmount(), PaymentMethod.CARD, "pay-" + UUID.randomUUID());
    }
    private boolean tryCreate(Fixture fixture) {
        try { service.create(fixture.request(), fixture.owner(), null); return true; }
        catch (BookingException exception) { assertEquals("VEHICLE_NOT_AVAILABLE", exception.getCode()); return false; }
    }
    private List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> wrappedFirst = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<Boolean> wrappedSecond = () -> { ready.countDown(); start.await(); return second.call(); };
            Future<Boolean> firstResult = executor.submit(wrappedFirst); Future<Boolean> secondResult = executor.submit(wrappedSecond);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }
    private Map<String, Object> body(Fixture fixture) {
        return Map.of("vehicleId", fixture.vehicle(), "startDateTime", fixture.start().toString(), "endDateTime", fixture.end().toString(), "pickupLocation", "Tunis", "dropoffLocation", "Tunis");
    }
    private RequestPostProcessor customer(Fixture fixture) {
        return user(fixture.email()).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("bookings:read"), new SimpleGrantedAuthority("bookings:create"), new SimpleGrantedAuthority("bookings:cancel"));
    }
    @Test void availabilitySpecificationRespectsBookingsHoldsGuaranteesAndHalfOpenPeriods() {
        Fixture fixture = fixture();
        Instant start = fixture.start(), end = fixture.end();
        assertTrue(available(start, end, ANY).contains(fixture.vehicle()));
        BookingSummary booking = service.create(fixture.request(), fixture.owner(), null);
        assertTrue(available(start, end, ANY).contains(fixture.vehicle()), "une demande PENDING ne bloque pas les dates");
        payInFull(booking.id(), fixture);
        service.transition(booking.id(), BookingStatus.CONFIRMED, admin(), null);
        assertFalse(available(start, end, ANY).contains(fixture.vehicle()), "une réservation confirmée bloque les dates");
        assertFalse(available(start.plusSeconds(3600), start.plusSeconds(7200), ANY).contains(fixture.vehicle()), "période incluse dans la réservation");
        assertFalse(available(end.minusSeconds(60), end.plusSeconds(60), ANY).contains(fixture.vehicle()), "chevauchement partiel");
        assertTrue(available(end, end.plusSeconds(3600), ANY).contains(fixture.vehicle()), "borne de fin exclue : période adjacente libre");
        assertTrue(available(start.minusSeconds(3600), start, ANY).contains(fixture.vehicle()), "borne de début exclue : période adjacente libre");
        Instant now = Instant.now();
        assertFalse(available(now.plusSeconds(61L * 86400), now.plusSeconds(62L * 86400), ANY).contains(fixture.vehicle()), "période au-delà de la garantie");
        assertFalse(available(now.minusSeconds(2L * 86400), now.minusSeconds(86400), ANY).contains(fixture.vehicle()), "période antérieure au début de la garantie");
    }
    @Test void availabilitySpecificationCombinesWithCatalogueFilters() {
        Fixture fixture = fixture();
        Instant start = fixture.start(), end = fixture.end();
        var brand = brands.findById(vehicles.findById(fixture.vehicle()).orElseThrow().getBrand().getId()).orElseThrow();
        assertTrue(available(start, end, new VehicleSearchRequest(null, brand.getSlug(), null, null, null, null, null, null, null, null, null)).contains(fixture.vehicle()));
        assertFalse(available(start, end, new VehicleSearchRequest(null, "autre-marque-inexistante", null, null, null, null, null, null, null, null, null)).contains(fixture.vehicle()));
        assertFalse(available(start, end, new VehicleSearchRequest(null, null, null, null, null, 5, null, null, null, null, null)).contains(fixture.vehicle()), "passagers insuffisants (véhicule de 4 places)");
        assertTrue(available(start, end, new VehicleSearchRequest(null, null, null, null, null, 4, null, null, null, null, null)).contains(fixture.vehicle()));
    }
    @Test void availabilitySpecificationExcludesVehiclesInMaintenanceOrArchived() {
        Fixture fixture = fixture();
        var vehicle = vehicles.findById(fixture.vehicle()).orElseThrow();
        vehicle.setStatus(VehicleStatus.MAINTENANCE); vehicles.saveAndFlush(vehicle);
        assertFalse(available(fixture.start(), fixture.end(), ANY).contains(fixture.vehicle()));
        vehicle.setStatus(VehicleStatus.ARCHIVED); vehicles.saveAndFlush(vehicle);
        assertFalse(available(fixture.start(), fixture.end(), ANY).contains(fixture.vehicle()));
    }
    private static final VehicleSearchRequest ANY = new VehicleSearchRequest(null, null, null, null, null, null, null, null, null, null, null);
    private List<UUID> available(Instant start, Instant end, VehicleSearchRequest filter) {
        return vehicles.findAll(VehicleSpecification.matching(filter, true).and(VehicleSpecification.availableDuring(start, end))).stream().map(VehicleEntity::getId).toList();
    }
    private UUID admin() {
        // Acteur technique unique : jamais l'e-mail du seeder, pour ne pas bloquer le vrai super-admin de test.
        UserEntity admin = new UserEntity(TestIdentities.email("booking-actor"), "not-a-login-password");
        admin.addRole(roles.findById("SUPER_ADMIN").orElseThrow());
        return users.saveAndFlush(admin).getId();
    }
    private BookingRequest request(Fixture fixture, Instant start, Instant end) { return new BookingRequest(fixture.vehicle(), start, end, "Tunis", "Tunis", null); }
    private Fixture fixture() {
        String email = TestIdentities.email("booking-customer"); String suffix = UUID.randomUUID().toString();
        UserEntity user = new UserEntity(email, "not-a-login-password"); user.addRole(roles.findById("CUSTOMER").orElseThrow()); users.saveAndFlush(user);
        customers.saveAndFlush(new CustomerEntity(user, "Booking", "Client", "+216 20 123 456"));
        var brand = brands.saveAndFlush(new BrandEntity("B " + suffix, "b-" + suffix, "Test", null, true));
        var category = categories.saveAndFlush(new CategoryEntity("C " + suffix, "c-" + suffix, "Test", true));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity(brand, category, "Touring", 2025, "Test", new BigDecimal("100.123"), new BigDecimal("650"), null, "TND", 4, 4, TransmissionType.AUTOMATIC, FuelType.PETROL, false, VehicleStatus.AVAILABLE));
        fixtures.guaranteeVehicle(vehicle.getId(), user.getId());
        Instant start = Instant.now().plusSeconds(30 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new Fixture(user.getId(), email, vehicle.getId(), start, start.plusSeconds(86400));
    }
    private record Fixture(UUID owner, String email, UUID vehicle, Instant start, Instant end) {
        BookingRequest request() { return new BookingRequest(vehicle, start, end, "Tunis", "Tunis", null);}
    }
}