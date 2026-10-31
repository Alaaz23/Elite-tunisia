package tn.elitetunisia.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.driver.DriverAssignmentService;
import tn.elitetunisia.driver.DriverEntity;
import tn.elitetunisia.driver.DriverRepository;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;

/** Règles métier et sécurité HTTP des transferts, avec de vraies données (H2 par défaut, PostgreSQL si demandé). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferWorkflowTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired TransferService service;
    @Autowired TransferRepository transfers;
    @Autowired TestFixtures fixtures;
    @Autowired DriverRepository drivers;
    @Autowired DriverAssignmentService assignments;
    @Autowired BookingService bookingService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EmployeeRepository employees;
    @Autowired MockMvc mvc;

    private TransferRequest request(TestFixtures.Fixture f, long hoursAfterStart, int passengers, UUID driverId) {
        return new TransferRequest(f.booking().id(), f.booking().startDateTime().plusSeconds(hoursAfterStart * 3600), "Aeroport Tunis-Carthage",
                "Hotel", passengers, driverId);
    }

    private DriverEntity driver(Instant licenseExpiry, boolean active) {
        var d = new DriverEntity("Transfer", "Driver", "+21600000000");
        d.setLicenseNumber("TR-" + UUID.randomUUID());
        d.setLicenseExpiry(licenseExpiry);
        d.setActive(active);
        return drivers.saveAndFlush(d);
    }

    private static void expect(String code, Runnable action) {
        assertEquals(code, assertThrows(DomainException.class, action::run).getCode());
    }

    private UUID staff() {
        var user = new UserEntity("staff-" + UUID.randomUUID() + "@example.test", "not-a-login-password");
        user.addRole(roles.findById("AGENT").orElseThrow());
        users.saveAndFlush(user);
        employees.saveAndFlush(new EmployeeEntity(user, "EMP-" + UUID.randomUUID().toString().substring(0, 8), "Agent", "Staff", "Member", "+216 20 000 000"));
        return user.getId();
    }

    @Test
    void creationDerivesVehicleFromBookingAndStartsPending() {
        var f = fixtures.createBooking(24);
        var created = service.create(request(f, 5, 3, null), staff(), null);
        assertEquals(TransferStatus.PENDING, created.getStatus());
        assertEquals(f.vehicle(), created.getVehicleId());
        assertEquals(f.booking().id(), created.getBookingId());
    }

    @Test
    void creationRejectsInvalidAssociations() {
        var f = fixtures.createBooking(24);
        UUID actor = staff();
        expect("TRANSFER_OUTSIDE_BOOKING_PERIOD", () -> service.create(request(f, -2, 2, null), actor, null));
        expect("TRANSFER_OUTSIDE_BOOKING_PERIOD", () -> service.create(request(f, 30, 2, null), actor, null));
        expect("TRANSFER_TOO_MANY_PASSENGERS", () -> service.create(request(f, 5, 6, null), actor, null));
        expect("BOOKING_NOT_FOUND", () -> service.create(new TransferRequest(UUID.randomUUID(), f.booking().startDateTime().plusSeconds(3600), "A", "B", 1, null), actor, null));
        expect("DRIVER_NOT_FOUND", () -> service.create(request(f, 5, 2, UUID.randomUUID()), actor, null));
    }

    @Test
    void cancelledBookingCannotReceiveTransfers() {
        var f = fixtures.createBooking(24);
        bookingService.customerCancel(f.booking().id(), f.owner(), null);
        expect("TRANSFER_BOOKING_STATUS", () -> service.create(request(f, 5, 2, null), staff(), null));
    }

    @Test
    void inactiveOrExpiredDriversAreRefused() {
        var f = fixtures.createBooking(24);
        UUID actor = staff();
        var inactive = driver(null, false);
        var expired = driver(f.booking().startDateTime().plusSeconds(3600), true);
        expect("DRIVER_INACTIVE", () -> service.create(request(f, 5, 2, inactive.getId()), actor, null));
        expect("DRIVER_LICENSE_EXPIRED", () -> service.create(request(f, 5, 2, expired.getId()), actor, null));
    }

    @Test
    void driverBusyOnAnotherBookingIsRefused() {
        var busy = fixtures.createBooking(24);
        var other = fixtures.createBooking(24);
        var d = driver(null, true);
        assignments.assignDriver(busy.booking().id(), d.getId());
        expect("DRIVER_NOT_AVAILABLE", () -> service.create(request(other, 5, 2, d.getId()), staff(), null));
    }

    @Test
    void bookingDriverIsUsedAndCannotBeOverriddenByTheClient() {
        var f = fixtures.createBooking(24);
        var assigned = driver(null, true);
        var stranger = driver(null, true);
        assignments.assignDriver(f.booking().id(), assigned.getId());
        UUID actor = staff();
        assertEquals(assigned.getId(), service.create(request(f, 5, 2, null), actor, null).getDriverId());
        expect("TRANSFER_DRIVER_MISMATCH", () -> service.create(request(f, 8, 2, stranger.getId()), actor, null));
    }

    @Test
    void sameDriverCannotHaveTwoTransfersWithinTheSlot() {
        var f = fixtures.createBooking(24);
        var d = driver(null, true);
        UUID actor = staff();
        service.create(request(f, 5, 2, d.getId()), actor, null);
        expect("DRIVER_NOT_AVAILABLE", () -> service.create(new TransferRequest(f.booking().id(),
                f.booking().startDateTime().plusSeconds(5 * 3600 + 1800), "A", "B", 2, d.getId()), actor, null));
        service.create(request(f, 7, 2, d.getId()), actor, null);
    }

    @Test
    void statusTransitionsAreEnforcedAndIdempotent() {
        var f = fixtures.createBooking(24);
        UUID actor = staff();
        var t = service.create(request(f, 5, 2, null), actor, null);
        expect("TRANSFER_INVALID_STATUS_TRANSITION", () -> service.transition(t.getId(), TransferStatus.COMPLETED, actor, null));
        assertEquals(TransferStatus.CONFIRMED, service.transition(t.getId(), TransferStatus.CONFIRMED, actor, null).getStatus());
        assertEquals(TransferStatus.CONFIRMED, service.transition(t.getId(), TransferStatus.CONFIRMED, actor, null).getStatus());
        assertEquals(TransferStatus.COMPLETED, service.transition(t.getId(), TransferStatus.COMPLETED, actor, null).getStatus());
        expect("TRANSFER_INVALID_STATUS_TRANSITION", () -> service.transition(t.getId(), TransferStatus.CANCELLED, actor, null));
        expect("TRANSFER_INVALID_STATUS_TRANSITION", () -> service.transition(t.getId(), TransferStatus.CONFIRMED, actor, null));
        expect("TRANSFER_NOT_FOUND", () -> service.transition(UUID.randomUUID(), TransferStatus.CONFIRMED, actor, null));
    }

    @Test
    void onlyActiveTransfersAreEditableAndTheEditRevalidates() {
        var f = fixtures.createBooking(24);
        UUID actor = staff();
        var t = service.create(request(f, 5, 2, null), actor, null);
        var edited = service.update(t.getId(), new TransferUpdateRequest(f.booking().startDateTime().plusSeconds(9 * 3600), "Nouvelle adresse", "Hotel", 4), actor, null);
        assertEquals("Nouvelle adresse", edited.getPickupAddress());
        assertEquals(4, edited.getPassengers());
        expect("TRANSFER_TOO_MANY_PASSENGERS", () -> service.update(t.getId(), new TransferUpdateRequest(f.booking().startDateTime().plusSeconds(9 * 3600), "A", "B", 9), actor, null));
        expect("TRANSFER_OUTSIDE_BOOKING_PERIOD", () -> service.update(t.getId(), new TransferUpdateRequest(f.booking().startDateTime().minusSeconds(60), "A", "B", 2), actor, null));
        service.transition(t.getId(), TransferStatus.CANCELLED, actor, null);
        expect("TRANSFER_NOT_EDITABLE", () -> service.update(t.getId(), new TransferUpdateRequest(f.booking().startDateTime().plusSeconds(9 * 3600), "A", "B", 2), actor, null));
    }

    @Test
    void legacyTransfersWithoutBookingCannotBeEdited() {
        var legacy = transfers.saveAndFlush(new TransferEntity());
        expect("TRANSFER_LEGACY_BOOKING_REQUIRED", () -> service.update(legacy.getId(),
                new TransferUpdateRequest(Instant.now().plusSeconds(3600), "A", "B", 1), staff(), null));
        assertEquals(TransferStatus.CANCELLED, service.transition(legacy.getId(), TransferStatus.CANCELLED, staff(), null).getStatus());
    }

    // ---- Couche HTTP : champs modifiables, validation, permissions ----

    private RequestPostProcessor staffWithBookingManagement() {
        var user = new UserEntity("http-staff-" + UUID.randomUUID() + "@example.test", "not-a-login-password");
        user.addRole(roles.findById("AGENT").orElseThrow());
        users.saveAndFlush(user);
        employees.saveAndFlush(new EmployeeEntity(user, "EMP-" + UUID.randomUUID().toString().substring(0, 8), "Agent", "Http", "Staff", "+216 20 000 000"));
        return SecurityMockMvcRequestPostProcessors.user(user.getEmail())
                .authorities(new SimpleGrantedAuthority("admin:access"), new SimpleGrantedAuthority("bookings:manage"));
    }

    @Test
    void forgedIdentifierStatusAndVehicleInTheBodyAreIgnored() throws Exception {
        var f = fixtures.createBooking(24);
        UUID forgedId = UUID.randomUUID();
        UUID forgedVehicle = UUID.randomUUID();
        String body = String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"%s\",\"pickupAddress\":\"Aeroport\",\"dropoffAddress\":\"Hotel\",\"passengers\":2,"
                + "\"id\":\"%s\",\"status\":\"COMPLETED\",\"vehicleId\":\"%s\"}", f.booking().id(), f.booking().startDateTime().plusSeconds(5 * 3600), forgedId, forgedVehicle);
        var result = mvc.perform(post("/api/admin/transfers").with(staffWithBookingManagement()).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.vehicleId").value(f.vehicle().toString()))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertNotEquals(forgedId.toString(), id);
        assertNotEquals(forgedVehicle.toString(), com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.vehicleId"));
    }

    @Test
    void invalidBodiesAreRejectedWith400() throws Exception {
        var f = fixtures.createBooking(24);
        String when = f.booking().startDateTime().plusSeconds(5 * 3600).toString();
        for (String body : new String[] {
                "{}",
                String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"%s\",\"pickupAddress\":\" \",\"dropoffAddress\":\"Hotel\",\"passengers\":2}", f.booking().id(), when),
                String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"%s\",\"pickupAddress\":\"A\",\"dropoffAddress\":\"Hotel\",\"passengers\":0}", f.booking().id(), when),
                String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"%s\",\"pickupAddress\":\"A\",\"dropoffAddress\":\"Hotel\",\"passengers\":101}", f.booking().id(), when),
                String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"not-a-date\",\"pickupAddress\":\"A\",\"dropoffAddress\":\"Hotel\",\"passengers\":2}", f.booking().id()) }) {
            mvc.perform(post("/api/admin/transfers").with(staffWithBookingManagement()).with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void domainErrorsAreTypedHttpResponsesNotServerErrors() throws Exception {
        var f = fixtures.createBooking(24);
        mvc.perform(get("/api/admin/transfers/" + UUID.randomUUID()).with(staffWithBookingManagement()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
        String late = f.booking().endDateTime().plusSeconds(3600).toString();
        mvc.perform(post("/api/admin/transfers").with(staffWithBookingManagement()).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\":\"%s\",\"pickupAt\":\"%s\",\"pickupAddress\":\"A\",\"dropoffAddress\":\"B\",\"passengers\":2}", f.booking().id(), late)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRANSFER_OUTSIDE_BOOKING_PERIOD"));
        mvc.perform(get("/api/admin/transfers").param("status", "NOPE").with(staffWithBookingManagement())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/transfers").param("size", "101").with(staffWithBookingManagement())).andExpect(status().isBadRequest());
    }

    @Test
    void writesRequireBookingManagementAndCsrf() throws Exception {
        var f = fixtures.createBooking(24);
        UUID actor = staff();
        var t = service.create(request(f, 5, 2, null), actor, null);
        RequestPostProcessor readOnlyStaff = SecurityMockMvcRequestPostProcessors.user("readonly@example.test")
                .authorities(new SimpleGrantedAuthority("admin:access"), new SimpleGrantedAuthority("bookings:read"));
        for (var path : new String[] { "confirm", "complete", "cancel" }) {
            mvc.perform(post("/api/admin/transfers/" + t.getId() + "/" + path).with(readOnlyStaff).with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/admin/transfers/" + t.getId()).with(readOnlyStaff).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"pickupAt\":\"%s\",\"pickupAddress\":\"A\",\"dropoffAddress\":\"B\",\"passengers\":2}", f.booking().startDateTime().plusSeconds(7200))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/transfers/" + t.getId() + "/confirm").with(staffWithBookingManagement()))
                .andExpect(status().isForbidden());
        assertEquals(TransferStatus.PENDING, service.get(t.getId()).getStatus());
    }

    @Test
    void customersCannotReachTransfers() throws Exception {
        mvc.perform(get("/api/admin/transfers").with(SecurityMockMvcRequestPostProcessors.user("client@example.test").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmCompleteAndListThroughHttp() throws Exception {
        var f = fixtures.createBooking(24);
        var t = service.create(request(f, 5, 2, null), staff(), null);
        var staff = staffWithBookingManagement();
        mvc.perform(post("/api/admin/transfers/" + t.getId() + "/confirm").with(staff).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/admin/transfers/" + t.getId() + "/cancel").with(staff).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/admin/transfers/" + t.getId() + "/complete").with(staff).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRANSFER_INVALID_STATUS_TRANSITION"));
        mvc.perform(get("/api/admin/transfers").param("bookingId", f.booking().id().toString()).with(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(t.getId().toString()));
    }
}
