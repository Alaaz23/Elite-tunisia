package tn.elitetunisia.partner;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeRequest;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeSummary;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;

/**
 * Garanties véhicule-partenaire. Le partenaire engagé est celui qui est actuellement rattaché au véhicule
 * au moment de la création ; il n'est jamais modifié ensuite.
 */
@Service
public class GuaranteeService {
    static final Duration MAX_DURATION = Duration.ofDays(90);
    static final List<BookingStatus> BLOCKING_WITHDRAWAL_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    private static final String EXCLUSION_VIOLATION_SQLSTATE = "23P01";

    private final GuaranteeRepository guarantees;
    private final PartnerRepository partners;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final AuditService audit;

    public GuaranteeService(GuaranteeRepository guarantees, PartnerRepository partners, VehicleRepository vehicles,
            UserRepository users, BookingRepository bookings, AuditService audit) {
        this.guarantees = guarantees;
        this.partners = partners;
        this.vehicles = vehicles;
        this.users = users;
        this.bookings = bookings;
        this.audit = audit;
    }

    @Transactional
    public GuaranteeSummary create(GuaranteeRequest request, UUID actorId, String ipAddress) {
        VehicleEntity vehicle = vehicles.findLocked(request.vehicleId())
                .orElseThrow(() -> DomainException.notFound("VEHICLE_NOT_FOUND"));
        PartnerEntity partner = vehicle.getPartner();
        if (partner == null) {
            throw DomainException.conflict("VEHICLE_HAS_NO_PARTNER");
        }
        if (!partner.isActive()) {
            throw DomainException.conflict("PARTNER_INACTIVE");
        }
        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw DomainException.conflict("VEHICLE_ARCHIVED");
        }
        Instant start = request.startsAt();
        Instant end = request.endsAt();
        Instant now = Instant.now();
        if (!end.isAfter(start)) {
            throw DomainException.invalid("GUARANTEE_PERIOD_INVALID");
        }
        if (start.isBefore(now)) {
            throw DomainException.invalid("GUARANTEE_START_IN_PAST");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw DomainException.invalid("GUARANTEE_TOO_LONG");
        }
        if (guarantees.countOverlapping(vehicle.getId(), GuaranteeStatus.ACTIVE, start, end) > 0) {
            throw DomainException.conflict("GUARANTEE_OVERLAP");
        }
        UserEntity actor = users.findById(actorId).orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND"));

        GuaranteeEntity saved;
        try {
            saved = guarantees.saveAndFlush(
                    new GuaranteeEntity(vehicle, partner, start, end, request.reason().trim(), actor, now));
        } catch (DataIntegrityViolationException ex) {
            if (hasSqlState(ex, EXCLUSION_VIOLATION_SQLSTATE)) {
                throw DomainException.conflict("GUARANTEE_OVERLAP");
            }
            throw ex;
        }
        audit.recordAudit(actorId, "GUARANTEE_CREATED", "VEHICLE", vehicle.getId(), ipAddress, "SUCCESS");
        return summary(saved);
    }

    /**
     * Retrait d'une garantie active non expirée, refusé si une réservation confirmée ou en cours
     * couvre la période de la garantie. Le véhicule est verrouillé avant la lecture de la garantie
     * pour lire son statut réellement commité.
     */
    @Transactional
    public GuaranteeSummary withdraw(UUID id, UUID actorId, String ipAddress) {
        UUID vehicleId = guarantees.findVehicleIdById(id)
                .orElseThrow(() -> DomainException.notFound("GUARANTEE_NOT_FOUND"));
        vehicles.findLocked(vehicleId).orElseThrow(() -> DomainException.notFound("VEHICLE_NOT_FOUND"));
        GuaranteeEntity guarantee = guarantees.findById(id)
                .orElseThrow(() -> DomainException.notFound("GUARANTEE_NOT_FOUND"));
        if (guarantee.getStatus() != GuaranteeStatus.ACTIVE) {
            throw DomainException.conflict("GUARANTEE_NOT_ACTIVE");
        }
        Instant now = Instant.now();
        if (!guarantee.getEndsAt().isAfter(now)) {
            throw DomainException.conflict("GUARANTEE_EXPIRED");
        }
        if (bookings.overlaps(vehicleId, guarantee.getStartsAt(), guarantee.getEndsAt(),
                BLOCKING_WITHDRAWAL_STATUSES, null) > 0) {
            throw DomainException.conflict("GUARANTEE_HAS_BOOKINGS");
        }
        UserEntity actor = users.findById(actorId).orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND"));
        guarantee.withdraw(actor, now);
        audit.recordAudit(actorId, "GUARANTEE_WITHDRAWN", "VEHICLE", vehicleId, ipAddress, "SUCCESS");
        return summary(guarantee);
    }

    @Transactional(readOnly = true)
    public GuaranteeSummary get(UUID id) {
        return summary(guarantees.findById(id).orElseThrow(() -> DomainException.notFound("GUARANTEE_NOT_FOUND")));
    }

    @Transactional(readOnly = true)
    public Page<GuaranteeSummary> list(Pageable pageable) {
        return guarantees.findAll(pageable).map(this::summary);
    }

    @Transactional(readOnly = true)
    public List<GuaranteeSummary> listForVehicle(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) {
            throw DomainException.notFound("VEHICLE_NOT_FOUND");
        }
        return guarantees.findByVehicleIdOrderByStartsAtDesc(vehicleId).stream().map(this::summary).toList();
    }

    /** Lecture limitée au partenaire lié à l'utilisateur courant. */
    @Transactional(readOnly = true)
    public List<GuaranteeSummary> ownGuarantees(UUID userId) {
        PartnerEntity partner = partners.findByUserId(userId)
                .orElseThrow(() -> DomainException.notFound("PARTNER_PROFILE_NOT_FOUND"));
        return guarantees.findByPartnerIdOrderByStartsAtDesc(partner.getId()).stream().map(this::summary).toList();
    }

    private static boolean hasSqlState(Throwable ex, String sqlState) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private GuaranteeSummary summary(GuaranteeEntity g) {
        return new GuaranteeSummary(g.getId(), g.getVehicle().getId(), g.getPartner().getId(), g.getPartner().getName(),
                g.getStartsAt(), g.getEndsAt(), g.getStatus(), g.getReason(), g.getCreatedBy().getId(), g.getCreatedAt(),
                g.getWithdrawnBy() == null ? null : g.getWithdrawnBy().getId(), g.getWithdrawnAt());
    }
}
