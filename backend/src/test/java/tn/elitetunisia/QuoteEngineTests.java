package tn.elitetunisia;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.*;
import tn.elitetunisia.customer.*;
import tn.elitetunisia.brand.*;
import tn.elitetunisia.category.*;
import tn.elitetunisia.vehicle.*;
import tn.elitetunisia.booking.*;
import tn.elitetunisia.quote.*;
import tn.elitetunisia.common.pricing.RentalPricingService;
@SpringBootTest(properties = "app.quotes.default-tax-amount=1.2345") @AutoConfigureMockMvc @ActiveProfiles("test")
class QuoteEngineTests {
    @Autowired QuoteService service;
    @Autowired QuoteRepository quotes;
    @Autowired BookingService bookingService;
    @Autowired VehicleRepository vehicles;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CustomerRepository customers;
    @Autowired ConfiguredServiceRepository services;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RentalPricingService pricing;
    private static final BigDecimal TAX = new BigDecimal("1.235");
    @Test void sharedPricingHandles24_25_48_49HoursAndConfiguredTaxRounding() {
        Instant start = Instant.now();
        for (int index = 0; index < 4; index++) {
            int hours = new int[] {24,25,48,49}[index]; int days = new int[] {1,2,2,3}[index];
            var result = pricing.calculate(new BigDecimal("500"), start, start.plusSeconds(hours * 3600L));
            assertEquals(days, result.days()); assertEquals(new BigDecimal(days * 500 + ".000"), result.amount());
        }
        assertEquals(TAX, new QuoteTaxPolicy(new BigDecimal("1.2345"), "TND").amountFor("TND"));
        assertThrows(QuoteException.class, () -> new QuoteTaxPolicy(BigDecimal.ONE, "TND").amountFor("USD"));
    }
    @Test void creationCalculatesOrderedRentalDriverServicesDiscountAndTax() {
        Fixture fixture = fixture(25);
        var extra = services.saveAndFlush(new ConfiguredServiceEntity("Configured extra", "Test", new BigDecimal("50"), "TND"));
        var quote = service.create(fixture.booking().id(), request(true, new BigDecimal("50"), List.of(new QuoteRequest.ServiceSelection(extra.getId(), 2))), admin(), null);
        assertEquals(QuoteStatus.DRAFT, quote.status()); assertEquals(1, quote.version());
        assertTrue(quote.reference().matches("DEV-[0-9]{4}-[0-9]{6,}"));
        assertEquals(new BigDecimal("1500.000"), quote.subtotal()); assertEquals(new BigDecimal("1451.235"), quote.totalAmount());
        assertEquals(List.of("VEHICLE_RENTAL","DRIVER","EXTRA_SERVICE","DISCOUNT","TAX"), quote.lines().stream().map(QuoteLineSummary::type).toList());
        for (int index = 0; index < quote.lines().size(); index++) assertEquals(index, quote.lines().get(index).position());
        assertEquals(2, quote.lines().getFirst().quantity()); assertEquals(new BigDecimal("200.000"), quote.lines().get(1).unitPrice());
    }
    @Test void sentSnapshotSurvivesPriceChangesAndNewVersionHasNewPrice() {
        Fixture fixture = fixture(24); var first = create(fixture);
        changeDailyRate(fixture, new BigDecimal("700"));
        assertEquals(new BigDecimal("501.235"), service.update(first.id(), request(false, BigDecimal.ZERO, List.of()), admin(), null).totalAmount());
        service.send(first.id(), admin(), null);
        assertEquals(new BigDecimal("501.235"), service.get(first.id(), fixture.owner()).totalAmount());
        var second = service.create(fixture.booking().id(), request(false, BigDecimal.ZERO, List.of()), admin(), null);
        assertEquals(2, second.version()); assertNotEquals(first.reference(), second.reference());
        assertEquals(new BigDecimal("701.235"), second.totalAmount());
        assertEquals(QuoteStatus.CANCELLED, service.get(first.id(), null).status());
        assertEquals(new BigDecimal("501.235"), service.get(first.id(), null).totalAmount());
    }
    @Test void acceptedAndRejectedVersionsCannotBeModified() {
        Fixture fixture = fixture(24); var quote = create(fixture); service.send(quote.id(), admin(), null);
        service.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        assertEquals(BookingStatus.PENDING, bookingService.adminGet(fixture.booking().id()).status());
        assertThrows(QuoteException.class, () -> service.update(quote.id(), request(false, BigDecimal.TEN, List.of()), admin(), null));
        assertThrows(QuoteException.class, () -> service.cancel(quote.id(), admin(), null));
        var second = service.create(fixture.booking().id(), request(false, BigDecimal.ZERO, List.of()), admin(), null); service.send(second.id(), admin(), null);
        service.customerAction(second.id(), fixture.owner(), "reject", "Non merci", null);
        assertThrows(QuoteException.class, () -> service.customerAction(second.id(), fixture.owner(), "accept", null, null));
        assertEquals(QuoteStatus.ACCEPTED, service.get(quote.id(), null).status());
    }
    @Test void expiryIsPersistedAuditedAndCannotBeAccepted() {
        Fixture fixture = fixture(24); var quote = create(fixture); service.send(quote.id(), admin(), null);
        jdbc.update("update quotes set valid_until = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), quote.id());
        assertEquals("QUOTE_EXPIRED", assertThrows(QuoteException.class, () -> service.customerAction(quote.id(), fixture.owner(), "accept", null, null)).getCode());
        assertEquals(QuoteStatus.EXPIRED, quotes.findById(quote.id()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action = 'QUOTE_EXPIRED'", Integer.class, quote.id()));
    }
    @Test void changeRequestCreatesHistoryAndDoesNotRewriteProposal() {
        Fixture fixture = fixture(24); var quote = create(fixture); service.send(quote.id(), admin(), null);
        var changed = service.customerAction(quote.id(), fixture.owner(), "request-change", "Ajouter un chauffeur", null);
        assertEquals(QuoteStatus.SENT, changed.status()); assertEquals(quote.totalAmount(), changed.totalAmount());
        assertTrue(changed.history().stream().anyMatch(event -> event.action().equals("QUOTE_CHANGE_REQUESTED") && event.message().contains("chauffeur")));
        assertThrows(QuoteException.class, () -> service.update(quote.id(), request(false, BigDecimal.ZERO, List.of()), admin(), null));
    }
    @Test void ownerIsolationAndCustomerCannotCreateOrForgeCommercialFields() throws Exception {
        Fixture fixture = fixture(24); Fixture other = fixture(24); var quote = create(fixture); service.send(quote.id(), admin(), null);
        var foreign = user(other.email()).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("quotes:read"), new SimpleGrantedAuthority("quotes:accept"));
        mvc.perform(get("/api/customer/quotes/{id}", quote.id()).with(foreign)).andExpect(status().isNotFound());
        mvc.perform(post("/api/customer/quotes/{id}/accept", quote.id()).with(foreign).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/quotes/booking/{id}", fixture.booking().id()).with(foreign).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        var owner = user(fixture.email()).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("quotes:read"), new SimpleGrantedAuthority("quotes:accept"));
        mvc.perform(post("/api/customer/quotes/{id}/accept", quote.id()).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalAmount\":1,\"unitPrice\":1,\"discountAmount\":999999,\"taxAmount\":0,\"reference\":\"FORGED\",\"version\":999,\"lines\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(501.235)).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.reference").value(quote.reference())).andExpect(jsonPath("$.internalNote").doesNotExist());
    }
    @Test void agencyConfirmationUsesAcceptedSnapshotAndRechecksAvailability() {
        Fixture fixture = fixture(24); var quote = service.create(fixture.booking().id(), request(true, BigDecimal.TEN, List.of()), admin(), null);
        assertEquals("QUOTE_NOT_ACCEPTED", assertThrows(BookingException.class, () -> bookingService.transition(fixture.booking().id(), BookingStatus.CONFIRMED, admin(), null)).getCode());
        service.send(quote.id(), admin(), null); service.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        var confirmed = bookingService.transition(fixture.booking().id(), BookingStatus.CONFIRMED, admin(), null);
        assertEquals(quote.totalAmount(), confirmed.totalAmount()); assertEquals(new BigDecimal("200.000"), confirmed.additionalCharges());
        Fixture conflict = fixture(24); var accepted = create(conflict); service.send(accepted.id(), admin(), null); service.customerAction(accepted.id(), conflict.owner(), "accept", null, null);
        var rival = bookingService.create(new BookingRequest(conflict.vehicle(), conflict.booking().startDateTime(), conflict.booking().endDateTime(), "Tunis", "Tunis", null), conflict.owner(), null);
        bookingService.transition(rival.id(), BookingStatus.CONFIRMED, admin(), null);
        assertEquals("VEHICLE_NOT_AVAILABLE", assertThrows(BookingException.class, () -> bookingService.transition(conflict.booking().id(), BookingStatus.CONFIRMED, admin(), null)).getCode());
        assertEquals(BookingStatus.PENDING, bookingService.adminGet(conflict.booking().id()).status());
    }
    @Test void draftUpdatesAndInvalidCommercialInputsAreControlled() {
        Fixture fixture = fixture(24); var quote = create(fixture);
        var updated = service.update(quote.id(), request(true, BigDecimal.TEN, List.of()), admin(), null); assertEquals(new BigDecimal("691.235"), updated.totalAmount());
        assertThrows(QuoteException.class, () -> service.update(quote.id(), request(false, new BigDecimal("999999"), List.of()), admin(), null));
        assertThrows(QuoteException.class, () -> service.update(quote.id(), new QuoteRequest(Instant.now().minusSeconds(1), false, BigDecimal.ZERO, null, null, List.of()), admin(), null));
        assertThrows(QuoteException.class, () -> service.update(quote.id(), request(false, BigDecimal.ZERO, List.of(new QuoteRequest.ServiceSelection(UUID.randomUUID(),1))), admin(), null));
        assertEquals(updated.totalAmount(), service.get(quote.id(), null).totalAmount());
    }
    @Test @EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
    void acceptedQuotesStillAllowOnlyOneConcurrentConfirmationOnPostgres() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("ELITE_POSTGRES_TEST")), "Postgres tests disabled");
        try (var connection = jdbc.getDataSource().getConnection()) { assertTrue(connection.getMetaData().getDatabaseProductName().contains("PostgreSQL")); }
        Fixture fixture = fixture(24); Fixture other = fixture(24);
        var secondBooking = bookingService.create(new BookingRequest(fixture.vehicle(), fixture.booking().startDateTime(), fixture.booking().endDateTime(), "Tunis", "Tunis", null), other.owner(), null);
        var firstQuote = create(fixture); var secondQuote = service.create(secondBooking.id(), request(false, BigDecimal.ZERO, List.of()), admin(), null);
        service.send(firstQuote.id(), admin(), null); service.send(secondQuote.id(), admin(), null);
        service.customerAction(firstQuote.id(), fixture.owner(), "accept", null, null); service.customerAction(secondQuote.id(), other.owner(), "accept", null, null);
        var ready = new java.util.concurrent.CountDownLatch(2); var start = new java.util.concurrent.CountDownLatch(1); UUID actor = admin();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { ready.countDown(); start.await(); return concurrentConfirm(fixture.booking().id(), actor); });
            var second = executor.submit(() -> { ready.countDown(); start.await(); return concurrentConfirm(secondBooking.id(), actor); });
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            var outcomes = List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        }
    }
    private boolean concurrentConfirm(UUID bookingId, UUID actor) {
        try { bookingService.transition(bookingId, BookingStatus.CONFIRMED, actor, null); return true; }
        catch (BookingException exception) { assertEquals("VEHICLE_NOT_AVAILABLE", exception.getCode()); return false; }
    }
    private QuoteRequest request(boolean driver, BigDecimal discount, List<QuoteRequest.ServiceSelection> selections) { return new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS), driver, discount, "Proposition", "Internal only", selections); }
    private QuoteSummary create(Fixture fixture) { return service.create(fixture.booking().id(), request(false, BigDecimal.ZERO, List.of()), admin(), null); }
    private UUID admin() { return users.findByEmailIgnoreCase("superadmin@example.test").orElseThrow().getId(); }
    private void changeDailyRate(Fixture fixture, BigDecimal rate) { jdbc.update("update vehicles set price_per_day = ? where id = ?", rate, fixture.vehicle()); }
    private Fixture fixture(int hours) {
        String suffix = UUID.randomUUID().toString(); String email = suffix + "@example.test";
        UserEntity user = new UserEntity(email, "not-a-login-password"); user.addRole(roles.findById("CUSTOMER").orElseThrow()); users.saveAndFlush(user);
        customers.saveAndFlush(new CustomerEntity(user, "Quote", "Client", "+216 20 123 456"));
        var brand = brands.saveAndFlush(new BrandEntity("B " + suffix, "b-" + suffix, "Test", null, true));
        var category = categories.saveAndFlush(new CategoryEntity("C " + suffix, "c-" + suffix, "Test", true));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity(brand, category, "Touring", 2025, "Test", new BigDecimal("500"), new BigDecimal("3000"), new BigDecimal("700"), "TND", 5,4,TransmissionType.AUTOMATIC,FuelType.PETROL,true,VehicleStatus.AVAILABLE));
        Instant start = Instant.now().plusSeconds(30 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new Fixture(user.getId(), email, vehicle.getId(), bookingService.create(new BookingRequest(vehicle.getId(), start, start.plusSeconds(hours * 3600L), "Tunis", "Tunis", null), user.getId(), null));
    }
    private record Fixture(UUID owner, String email, UUID vehicle, BookingSummary booking) { }
}