package tn.elitetunisia.partner;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.TestIdentities;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.booking.BookingException;
import tn.elitetunisia.booking.BookingRequest;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryEntity;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.customer.CustomerEntity;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeRequest;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeSummary;
import tn.elitetunisia.vehicle.FuelType;
import tn.elitetunisia.vehicle.TransmissionType;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleService;
import tn.elitetunisia.vehicle.VehicleStatus;
import tn.elitetunisia.vehicle.VehicleStatusRequest;

/**
 * Disponibilité véhicules et partenaires sur PostgreSQL réel : garanties, retraits, transferts, accès partenaire,
 * concurrence et intégrité. Chaque tour crée ses propres véhicules, partenaires et clients.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
class PartnerAvailabilityPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final int SEQUENTIAL_ROUNDS = 5;
    private static final int RACE_ROUNDS = 10;

    @Autowired GuaranteeService guaranteeService;
    @Autowired PartnerService partnerService;
    @Autowired VehicleService vehicleService;
    @Autowired BookingService bookingService;
    @Autowired TestFixtures fixtures;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired VehicleRepository vehicles;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    private ExecutorService executor;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    /** Scénarios 1 et 12 : garantie créée puis réservation confirmée dans la période garantie. */
    @Test
    void guaranteeThenBookingInsidePeriodConfirms() {
        for (int round = 0; round < SEQUENTIAL_ROUNDS; round++) {
            Instant base = nowPlus(1);
            var partner = partnerAccount("s1");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID owner = customer();
            guaranteeService.create(new GuaranteeRequest(vehicle, base, base.plus(29, ChronoUnit.DAYS), "garantie"), owner, null);

            UUID booking = bookingService.create(new BookingRequest(vehicle, base.plus(1, ChronoUnit.DAYS),
                    base.plus(2, ChronoUnit.DAYS), "Tunis", "Tunis", null), owner, null).id();
            fixtures.payInFull(booking, owner);
            bookingService.transition(booking, BookingStatus.CONFIRMED, owner, null);

            assertEquals("CONFIRMED", bookingStatus(booking), "round " + round);
            assertEquals(0, uncoveredActiveBookings(vehicle), "round " + round);
        }
    }

    /** Scénario 2 : deux garanties disjointes créées simultanément sur le même véhicule : les deux réussissent. */
    @Test
    void disjointGuaranteesBothCreated() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            var partner = partnerAccount("s2");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID actor = customer();
            Instant base = nowPlus(1);
            List<String> outcomes = race(
                    () -> guaranteeService.create(new GuaranteeRequest(vehicle, base, base.plus(5, ChronoUnit.DAYS), "a"), actor, null),
                    () -> guaranteeService.create(new GuaranteeRequest(vehicle, base.plus(10, ChronoUnit.DAYS),
                            base.plus(15, ChronoUnit.DAYS), "b"), actor, null));

            assertEquals(List.of("OK", "OK"), outcomes, "round " + round);
            assertEquals(2, activeGuarantees(vehicle), "round " + round);
            assertEquals(0, overlappingActiveGuarantees(vehicle), "round " + round);
        }
    }

    /** Scénarios 3 et 4 : chevauchements partiel et total simultanés : une seule garantie doit être créée. */
    @Test
    void partialOverlappingGuaranteesOnlyOneCreated() throws Exception {
        assertSingleGuaranteeWins(0, 12);
    }

    @Test
    void totalOverlappingGuaranteesOnlyOneCreated() throws Exception {
        assertSingleGuaranteeWins(0, 0);
    }

    /** Scénario 6 : chevauchement refusé même en séquence, et l'état reste inchangé. */
    @Test
    void sequentialOverlapIsRefusedWithoutSideEffect() {
        var partner = partnerAccount("s6");
        UUID vehicle = vehicleOf(partner.partner()).getId();
        UUID actor = customer();
        Instant base = nowPlus(1);
        guaranteeService.create(new GuaranteeRequest(vehicle, base, base.plus(10, ChronoUnit.DAYS), "a"), actor, null);

        DomainException refused = assertThrows(DomainException.class, () -> guaranteeService.create(
                new GuaranteeRequest(vehicle, base.plus(2, ChronoUnit.DAYS), base.plus(3, ChronoUnit.DAYS), "b"), actor, null));
        assertEquals("GUARANTEE_OVERLAP", refused.getCode());
        assertEquals(1, activeGuarantees(vehicle));
    }

    /**
     * Scénario 5 : retrait de garantie simultané à une confirmation client. Invariant : jamais de réservation
     * CONFIRMED couverte par une garantie retirée.
     */
    @Test
    void withdrawalRacingConfirmationNeverLeavesUncoveredBooking() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            var partner = partnerAccount("s5");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID owner = customer();
            Instant base = nowPlus(1);
            GuaranteeSummary guarantee = guaranteeService.create(
                    new GuaranteeRequest(vehicle, base, base.plus(29, ChronoUnit.DAYS), "garantie"), owner, null);
            UUID booking = bookingService.create(new BookingRequest(vehicle, base.plus(1, ChronoUnit.DAYS),
                    base.plus(2, ChronoUnit.DAYS), "Tunis", "Tunis", null), owner, null).id();
            fixtures.payInFull(booking, owner);

            List<String> outcomes = race(
                    () -> guaranteeService.withdraw(guarantee.id(), owner, null),
                    () -> bookingService.transition(booking, BookingStatus.CONFIRMED, owner, null));

            String guaranteeStatus = guaranteeStatus(guarantee.id());
            String bookingStatus = bookingStatus(booking);
            assertFalse("WITHDRAWN".equals(guaranteeStatus) && "CONFIRMED".equals(bookingStatus),
                    "round " + round + " outcomes " + outcomes);
            assertEquals(0, uncoveredActiveBookings(vehicle), "round " + round + " outcomes " + outcomes);
            assertTrue(outcomes.get(0).equals("OK") || outcomes.get(0).equals("GUARANTEE_HAS_BOOKINGS"),
                    "round " + round + " outcomes " + outcomes);
        }
    }

    /** Scénario 9 : retrait refusé tant qu'une réservation CONFIRMED est couverte par la garantie. */
    @Test
    void withdrawalRefusedWhileConfirmedBookingIsCovered() {
        var partner = partnerAccount("s9a");
        UUID vehicle = vehicleOf(partner.partner()).getId();
        UUID owner = customer();
        Instant base = nowPlus(1);
        GuaranteeSummary guarantee = guaranteeService.create(
                new GuaranteeRequest(vehicle, base, base.plus(29, ChronoUnit.DAYS), "garantie"), owner, null);
        UUID booking = bookingService.create(new BookingRequest(vehicle, base.plus(1, ChronoUnit.DAYS),
                base.plus(2, ChronoUnit.DAYS), "Tunis", "Tunis", null), owner, null).id();
        fixtures.payInFull(booking, owner);
        bookingService.transition(booking, BookingStatus.CONFIRMED, owner, null);

        DomainException refused = assertThrows(DomainException.class,
                () -> guaranteeService.withdraw(guarantee.id(), owner, null));
        assertEquals("GUARANTEE_HAS_BOOKINGS", refused.getCode());
        assertEquals("ACTIVE", guaranteeStatus(guarantee.id()));
    }

    /**
     * Scénario 9 (suite) : une réservation PENDING ne bloque jamais le retrait, et un retrait sur une période future
     * sans réservation est autorisé. Un second retrait est refusé et ne modifie pas l'horodatage initial.
     */
    @Test
    void withdrawalAllowedWithPendingOnlyAndFuturePeriod() {
        var partner = partnerAccount("s9b");
        UUID vehicle = vehicleOf(partner.partner()).getId();
        UUID owner = customer();
        Instant base = nowPlus(1);
        GuaranteeSummary covered = guaranteeService.create(
                new GuaranteeRequest(vehicle, base, base.plus(5, ChronoUnit.DAYS), "a"), owner, null);
        GuaranteeSummary future = guaranteeService.create(
                new GuaranteeRequest(vehicle, base.plus(20, ChronoUnit.DAYS), base.plus(25, ChronoUnit.DAYS), "b"), owner, null);
        UUID pending = bookingService.create(new BookingRequest(vehicle, base.plus(1, ChronoUnit.DAYS),
                base.plus(2, ChronoUnit.DAYS), "Tunis", "Tunis", null), owner, null).id();

        guaranteeService.withdraw(covered.id(), owner, null);
        guaranteeService.withdraw(future.id(), owner, null);

        assertEquals("WITHDRAWN", guaranteeStatus(covered.id()));
        assertEquals("WITHDRAWN", guaranteeStatus(future.id()));
        assertEquals("PENDING", bookingStatus(pending));
        Timestamp firstWithdrawal = withdrawnAt(covered.id());
        DomainException again = assertThrows(DomainException.class,
                () -> guaranteeService.withdraw(covered.id(), owner, null));
        assertEquals("GUARANTEE_NOT_ACTIVE", again.getCode());
        assertEquals(firstWithdrawal, withdrawnAt(covered.id()));
    }

    /** Scénario 7 : deux retraits simultanés de la même garantie : un seul réussit, l'autre est refusé. */
    @Test
    void concurrentWithdrawalsOfSameGuaranteeSucceedOnce() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            var partner = partnerAccount("s7");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID actor = customer();
            Instant base = nowPlus(1);
            UUID guarantee = guaranteeService.create(
                    new GuaranteeRequest(vehicle, base, base.plus(29, ChronoUnit.DAYS), "garantie"), actor, null).id();

            List<String> outcomes = race(
                    () -> guaranteeService.withdraw(guarantee, actor, null),
                    () -> guaranteeService.withdraw(guarantee, actor, null));

            assertEquals(1, count(outcomes, "OK"), "round " + round + " " + outcomes);
            assertEquals(1, count(outcomes, "GUARANTEE_NOT_ACTIVE"), "round " + round + " " + outcomes);
            assertEquals("WITHDRAWN", guaranteeStatus(guarantee), "round " + round);
        }
    }

    /**
     * Scénario 8 : retrait de garantie et passage en maintenance simultanés alors qu'une réservation est CONFIRMED.
     * Invariant : le retrait et la maintenance sont refusés, le véhicule reste disponible et la réservation confirmée.
     */
    @Test
    void maintenanceAndWithdrawalRacingWithConfirmedBookingBothRefused() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            var partner = partnerAccount("s8");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID owner = customer();
            Instant base = nowPlus(1);
            UUID guarantee = guaranteeService.create(
                    new GuaranteeRequest(vehicle, base, base.plus(29, ChronoUnit.DAYS), "garantie"), owner, null).id();
            UUID booking = bookingService.create(new BookingRequest(vehicle, base.plus(1, ChronoUnit.DAYS),
                    base.plus(2, ChronoUnit.DAYS), "Tunis", "Tunis", null), owner, null).id();
            fixtures.payInFull(booking, owner);
            bookingService.transition(booking, BookingStatus.CONFIRMED, owner, null);

            List<String> outcomes = race(
                    () -> guaranteeService.withdraw(guarantee, owner, null),
                    () -> vehicleService.updateStatus(vehicle, new VehicleStatusRequest(VehicleStatus.MAINTENANCE), owner, null));

            assertEquals("GUARANTEE_HAS_BOOKINGS", outcomes.get(0), "round " + round);
            assertNotEquals("OK", outcomes.get(1), "round " + round + " maintenance accepted over CONFIRMED booking");
            assertEquals("ACTIVE", guaranteeStatus(guarantee), "round " + round);
            assertEquals("CONFIRMED", bookingStatus(booking), "round " + round);
            assertEquals("AVAILABLE", vehicleStatus(vehicle), "round " + round);
        }
    }

    /** Scénario 10 : un partenaire ne voit ni le véhicule ni les garanties d'un autre partenaire. */
    @Test
    void partnerCannotAccessAnotherPartnersVehicleOrGuarantees() {
        var owner = partnerAccount("s10a");
        var other = partnerAccount("s10b");
        UUID vehicle = vehicleOf(owner.partner()).getId();
        UUID actor = customer();
        Instant base = nowPlus(1);
        GuaranteeSummary guarantee = guaranteeService.create(
                new GuaranteeRequest(vehicle, base, base.plus(5, ChronoUnit.DAYS), "garantie"), actor, null);

        assertEquals(vehicle, partnerService.ownVehicle(owner.userId(), vehicle).id());
        DomainException denied = assertThrows(DomainException.class,
                () -> partnerService.ownVehicle(other.userId(), vehicle));
        assertEquals("VEHICLE_NOT_FOUND", denied.getCode());
        assertTrue(guaranteeService.ownGuarantees(other.userId()).stream().noneMatch(g -> g.id().equals(guarantee.id())));
        assertTrue(guaranteeService.ownGuarantees(owner.userId()).stream().anyMatch(g -> g.id().equals(guarantee.id())));
    }

    /**
     * Scénario 11 : échec transactionnel à la création (acteur inexistant) : aucune garantie partielle n'est persistée.
     * Limite : l'échec est détecté avant l'insertion ; le rollback après insertion n'est pas simulé ici.
     */
    @Test
    void failedCreationLeavesNoGuarantee() {
        var partner = partnerAccount("s11");
        UUID vehicle = vehicleOf(partner.partner()).getId();
        Instant base = nowPlus(1);

        DomainException failed = assertThrows(DomainException.class, () -> guaranteeService.create(
                new GuaranteeRequest(vehicle, base, base.plus(5, ChronoUnit.DAYS), "garantie"), UUID.randomUUID(), null));
        assertEquals("USER_NOT_FOUND", failed.getCode());
        assertEquals(0, activeGuarantees(vehicle));
    }

    /**
     * Scénario 12 : contrôles d'intégrité en base. Les chevauchements, les retraits incohérents, la suppression et la
     * modification d'une garantie sont refusés par les triggers PostgreSQL, indépendamment du service.
     */
    @Test
    void databaseRejectsOverlapDeletionAndIllegalUpdates() {
        var partner = partnerAccount("s12");
        UUID vehicle = vehicleOf(partner.partner()).getId();
        UUID actor = customer();
        Instant base = nowPlus(1);
        UUID guarantee = guaranteeService.create(
                new GuaranteeRequest(vehicle, base, base.plus(5, ChronoUnit.DAYS), "garantie"), actor, null).id();
        UUID partnerId = partner.partner().getId();

        assertThrows(Exception.class, () -> jdbc.update(
                "insert into vehicle_guarantees (id, vehicle_id, partner_id, starts_at, ends_at, status, reason, created_by_user_id)"
                        + " values (?, ?, ?, ?, ?, 'ACTIVE', 'direct', ?)",
                UUID.randomUUID(), vehicle, partnerId, Timestamp.from(base.plus(1, ChronoUnit.DAYS)),
                Timestamp.from(base.plus(2, ChronoUnit.DAYS)), actor));
        assertThrows(Exception.class, () -> jdbc.update("delete from vehicle_guarantees where id = ?", guarantee));
        assertThrows(Exception.class, () -> jdbc.update(
                "update vehicle_guarantees set ends_at = ends_at + interval '1 day' where id = ?", guarantee));
        assertThrows(Exception.class, () -> jdbc.update(
                "update vehicle_guarantees set partner_id = ? where id = ?", UUID.randomUUID(), guarantee));

        assertEquals(1, activeGuarantees(vehicle));
        assertEquals(0, overlappingActiveGuarantees(vehicle));
    }

    /** Scénarios 3 et 4 : une seule garantie chevauchante peut être créée, quel que soit l'ordre de course. */
    private void assertSingleGuaranteeWins(long firstShiftHours, long secondShiftHours) throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            var partner = partnerAccount("overlap");
            UUID vehicle = vehicleOf(partner.partner()).getId();
            UUID actor = customer();
            Instant base = nowPlus(1);
            Instant secondStart = base.plus(secondShiftHours, ChronoUnit.HOURS);
            List<String> outcomes = race(
                    () -> guaranteeService.create(new GuaranteeRequest(vehicle, base.plus(firstShiftHours, ChronoUnit.HOURS),
                            base.plus(firstShiftHours + 48, ChronoUnit.HOURS), "a"), actor, null),
                    () -> guaranteeService.create(new GuaranteeRequest(vehicle, secondStart,
                            secondStart.plus(48, ChronoUnit.HOURS), "b"), actor, null));

            assertEquals(1, count(outcomes, "OK"), "round " + round + " " + outcomes);
            assertEquals(1, count(outcomes, "GUARANTEE_OVERLAP"), "round " + round + " " + outcomes);
            assertEquals(1, activeGuarantees(vehicle), "round " + round);
            assertEquals(0, overlappingActiveGuarantees(vehicle), "round " + round);
        }
    }

    /** Lance les actions en même temps ; retourne "OK" ou le code métier de chaque action, dans l'ordre fourni. */
    private List<String> race(Callable<?>... actions) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<?> action : actions) {
            futures.add(executor.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                try {
                    action.call();
                    return "OK";
                } catch (DomainException ex) {
                    return ex.getCode();
                } catch (BookingException ex) {
                    return ex.getCode();
                }
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private PartnerAccount partnerAccount(String label) {
        var user = users.saveAndFlush(new UserEntity(TestIdentities.email("partner-" + label), "not-a-login-password"));
        var partner = new PartnerEntity("Partner " + label + " " + UUID.randomUUID().toString().substring(0, 8), PartnerType.COMPANY);
        partner.update(partner.getName(), PartnerType.COMPANY, null, null, null, null, null, user);
        return new PartnerAccount(partners.saveAndFlush(partner), user.getId());
    }

    private VehicleEntity vehicleOf(PartnerEntity partner) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var brand = brands.saveAndFlush(new BrandEntity("B " + suffix, "b-" + suffix, "Test", null, true));
        var category = categories.saveAndFlush(new CategoryEntity("C " + suffix, "c-" + suffix, "Test", true));
        var vehicle = new VehicleEntity(brand, category, "Model", 2025, "Test", new BigDecimal("500"), new BigDecimal("3000"),
                new BigDecimal("700"), "TND", 5, 4, TransmissionType.AUTOMATIC, FuelType.PETROL, true, VehicleStatus.AVAILABLE);
        vehicle.setPartner(partner);
        return vehicles.saveAndFlush(vehicle);
    }

    private UUID customer() {
        var user = users.saveAndFlush(new UserEntity(TestIdentities.email("avail-customer"), "not-a-login-password"));
        customers.saveAndFlush(new CustomerEntity(user, "Test", "Client", "+21600000000"));
        return user.getId();
    }

    private static Instant nowPlus(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private static long count(List<String> outcomes, String code) {
        return outcomes.stream().filter(code::equals).count();
    }

    private record PartnerAccount(PartnerEntity partner, UUID userId) { }

    private String bookingStatus(UUID bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    private String guaranteeStatus(UUID guaranteeId) {
        return jdbc.queryForObject("select status from vehicle_guarantees where id = ?", String.class, guaranteeId);
    }

    private Timestamp withdrawnAt(UUID guaranteeId) {
        return jdbc.queryForObject("select withdrawn_at from vehicle_guarantees where id = ?", Timestamp.class, guaranteeId);
    }

    private String vehicleStatus(UUID vehicleId) {
        return jdbc.queryForObject("select status from vehicles where id = ?", String.class, vehicleId);
    }

    private int activeGuarantees(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from vehicle_guarantees where vehicle_id = ? and status = 'ACTIVE'",
                Integer.class, vehicleId);
    }

    private int overlappingActiveGuarantees(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from vehicle_guarantees a join vehicle_guarantees b on a.id < b.id"
                + " and a.vehicle_id = ? and b.vehicle_id = ? and a.status = 'ACTIVE' and b.status = 'ACTIVE'"
                + " and a.starts_at < b.ends_at and b.starts_at < a.ends_at", Integer.class, vehicleId, vehicleId);
    }

    /** Réservations bloquantes non couvertes par une garantie ACTIVE du même véhicule. */
    private int uncoveredActiveBookings(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from bookings b where b.vehicle_id = ?"
                + " and b.status in ('CONFIRMED', 'IN_PROGRESS') and not exists (select 1 from vehicle_guarantees g"
                + " where g.vehicle_id = b.vehicle_id and g.status = 'ACTIVE' and g.starts_at <= b.starts_at"
                + " and g.ends_at >= b.ends_at)", Integer.class, vehicleId);
    }
}
