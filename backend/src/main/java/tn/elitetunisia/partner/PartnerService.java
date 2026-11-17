package tn.elitetunisia.partner;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import tn.elitetunisia.partner.PartnerDtos.AssignVehicleRequest;
import tn.elitetunisia.partner.PartnerDtos.AssignmentSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerRequest;
import tn.elitetunisia.partner.PartnerDtos.PartnerSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerVehicleSummary;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;

@Service
public class PartnerService {
    static final List<BookingStatus> BLOCKING_TRANSFER_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final PartnerRepository partners;
    private final VehiclePartnerAssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final GuaranteeRepository guarantees;
    private final AuditService audit;

    public PartnerService(PartnerRepository partners, VehiclePartnerAssignmentRepository assignments,
            VehicleRepository vehicles, UserRepository users, BookingRepository bookings,
            GuaranteeRepository guarantees, AuditService audit) {
        this.partners = partners;
        this.assignments = assignments;
        this.vehicles = vehicles;
        this.users = users;
        this.bookings = bookings;
        this.guarantees = guarantees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<PartnerSummary> list(Pageable pageable) {
        return partners.findAll(pageable).map(this::summary);
    }

    @Transactional(readOnly = true)
    public PartnerSummary get(UUID id) {
        return summary(find(id));
    }

    @Transactional
    public PartnerSummary create(PartnerRequest request, UUID actorId, String ipAddress) {
        PartnerEntity partner = new PartnerEntity(normalizeName(request.name()), request.partnerType());
        apply(partner, request);
        PartnerEntity saved = partners.save(partner);
        audit.recordAudit(actorId, "PARTNER_CREATED", "PARTNER", saved.getId(), ipAddress, "SUCCESS");
        return summary(saved);
    }

    @Transactional
    public PartnerSummary update(UUID id, PartnerRequest request, UUID actorId, String ipAddress) {
        PartnerEntity partner = find(id);
        apply(partner, request);
        audit.recordAudit(actorId, "PARTNER_UPDATED", "PARTNER", id, ipAddress, "SUCCESS");
        return summary(partner);
    }

    @Transactional
    public PartnerSummary deactivate(UUID id, UUID actorId, String ipAddress) {
        PartnerEntity partner = find(id);
        if (!partner.isActive()) {
            return summary(partner);
        }
        partner.deactivate(Instant.now());
        audit.recordAudit(actorId, "PARTNER_DEACTIVATED", "PARTNER", id, ipAddress, "SUCCESS");
        return summary(partner);
    }

    @Transactional
    public PartnerSummary activate(UUID id, UUID actorId, String ipAddress) {
        PartnerEntity partner = find(id);
        if (partner.isActive()) {
            return summary(partner);
        }
        partner.activate();
        audit.recordAudit(actorId, "PARTNER_ACTIVATED", "PARTNER", id, ipAddress, "SUCCESS");
        return summary(partner);
    }

    /**
     * Affecte un véhicule à un partenaire. Sans affectation courante, c'est une première affectation.
     * Avec une affectation courante, c'est un transfert : refusé si une réservation bloquante existe.
     * Les garanties historiques ne sont jamais modifiées ici.
     */
    @Transactional
    public AssignmentSummary assign(UUID vehicleId, AssignVehicleRequest request, UUID actorId, String ipAddress) {
        VehicleEntity vehicle = vehicles.findLocked(vehicleId)
                .orElseThrow(() -> DomainException.notFound("VEHICLE_NOT_FOUND"));
        PartnerEntity partner = find(request.partnerId());
        if (!partner.isActive()) {
            throw DomainException.conflict("PARTNER_INACTIVE");
        }
        UserEntity actor = users.findById(actorId).orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND"));
        Instant now = Instant.now();

        var current = assignments.findFirstByVehicleIdAndEndedAtIsNull(vehicleId);
        if (current.isPresent()) {
            if (current.get().getPartner().getId().equals(partner.getId())) {
                throw DomainException.conflict("VEHICLE_ALREADY_ASSIGNED_TO_PARTNER");
            }
            if (guarantees.countActiveEndingAfter(vehicleId, GuaranteeStatus.ACTIVE, now) > 0) {
                throw DomainException.conflict("VEHICLE_TRANSFER_BLOCKED_BY_GUARANTEE");
            }
            if (bookings.countByVehicle(vehicleId, BLOCKING_TRANSFER_STATUSES) > 0) {
                throw DomainException.conflict("VEHICLE_TRANSFER_BLOCKED_BY_BOOKING");
            }
            current.get().close(now);
            assignments.saveAndFlush(current.get());
        }

        VehiclePartnerAssignmentEntity assignment =
                assignments.save(new VehiclePartnerAssignmentEntity(vehicle, partner, request.reason().trim(), actor, now));
        vehicle.setPartner(partner);
        audit.recordAudit(actorId, current.isPresent() ? "VEHICLE_PARTNER_TRANSFERRED" : "VEHICLE_PARTNER_ASSIGNED",
                "VEHICLE", vehicleId, ipAddress, "SUCCESS");
        return assignmentSummary(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentSummary> history(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) {
            throw DomainException.notFound("VEHICLE_NOT_FOUND");
        }
        return assignments.findByVehicleIdOrderByAssignedAtDesc(vehicleId).stream().map(this::assignmentSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerVehicleSummary> vehiclesOf(UUID partnerId) {
        find(partnerId);
        return vehicles.findByPartnerIdOrderByModelAsc(partnerId).stream().map(this::vehicleSummary).toList();
    }

    @Transactional(readOnly = true)
    public PartnerSummary ownProfile(UUID userId) {
        return summary(ownPartner(userId));
    }

    /** Lecture limitée au partenaire lié à l'utilisateur courant. */
    @Transactional(readOnly = true)
    public List<PartnerVehicleSummary> ownVehicles(UUID userId) {
        return vehicles.findByPartnerIdOrderByModelAsc(ownPartner(userId).getId()).stream().map(this::vehicleSummary).toList();
    }

    /** Un véhicule d'un autre partenaire est signalé comme introuvable pour ne pas révéler son existence. */
    @Transactional(readOnly = true)
    public PartnerVehicleSummary ownVehicle(UUID userId, UUID vehicleId) {
        PartnerEntity partner = ownPartner(userId);
        VehicleEntity vehicle = vehicles.findById(vehicleId)
                .filter(v -> v.getPartner() != null && v.getPartner().getId().equals(partner.getId()))
                .orElseThrow(() -> DomainException.notFound("VEHICLE_NOT_FOUND"));
        return vehicleSummary(vehicle);
    }

    private PartnerEntity ownPartner(UUID userId) {
        return partners.findByUserId(userId).orElseThrow(() -> DomainException.notFound("PARTNER_PROFILE_NOT_FOUND"));
    }

    private void apply(PartnerEntity partner, PartnerRequest request) {
        if (request.partnerType() == PartnerType.COMPANY && isBlank(request.taxIdentifier())) {
            throw DomainException.invalid("PARTNER_TAX_IDENTIFIER_REQUIRED");
        }
        if (request.partnerType() == PartnerType.INDIVIDUAL && !isBlank(request.taxIdentifier())) {
            throw DomainException.invalid("PARTNER_TAX_IDENTIFIER_NOT_ALLOWED");
        }
        UserEntity user = null;
        if (request.userId() != null) {
            user = users.findById(request.userId()).orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND"));
            if (!user.getRoleKeys().contains("PARTNER")) {
                throw DomainException.invalid("USER_NOT_PARTNER_ROLE");
            }
            partners.findByUserId(user.getId())
                    .filter(other -> !other.getId().equals(partner.getId()))
                    .ifPresent(other -> { throw DomainException.conflict("USER_ALREADY_LINKED_TO_PARTNER"); });
        }
        partner.update(normalizeName(request.name()), request.partnerType(), blankToNull(request.email()),
                blankToNull(request.phone()), blankToNull(request.address()), blankToNull(request.city()),
                blankToNull(request.taxIdentifier()), user);
    }

    private PartnerEntity find(UUID id) {
        return partners.findById(id).orElseThrow(() -> DomainException.notFound("PARTNER_NOT_FOUND"));
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw DomainException.invalid("PARTNER_NAME_REQUIRED");
        }
        return name.trim();
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }

    private PartnerSummary summary(PartnerEntity p) {
        return new PartnerSummary(p.getId(), p.getName(), p.getPartnerType(), p.getEmail(), p.getPhone(), p.getAddress(),
                p.getCity(), p.getTaxIdentifier(), p.isActive(), p.getUser() == null ? null : p.getUser().getId(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getDeactivatedAt());
    }

    private AssignmentSummary assignmentSummary(VehiclePartnerAssignmentEntity a) {
        return new AssignmentSummary(a.getId(), a.getVehicle().getId(), a.getPartner().getId(), a.getPartner().getName(),
                a.getAssignedAt(), a.getEndedAt(), a.getReason(), a.getAssignedBy().getId());
    }

    private PartnerVehicleSummary vehicleSummary(VehicleEntity v) {
        return new PartnerVehicleSummary(v.getId(), v.getBrand().getName(), v.getModel(), v.getYear(),
                v.getStatus().name());
    }
}
