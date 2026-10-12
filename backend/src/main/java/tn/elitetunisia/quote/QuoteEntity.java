package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.elitetunisia.booking.BookingEntity;
@Entity @Table(name = "quotes")
public class QuoteEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "vehicle_id", nullable = false) private UUID vehicleId;
    @Column(nullable = false, unique = true, length = 40) private String reference;
    @Column(nullable = false) private int version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private QuoteStatus status = QuoteStatus.DRAFT;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 3) private BigDecimal discountAmount;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 3) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 3) private BigDecimal totalAmount;
    @Column(name = "valid_until", nullable = false) private Instant validUntil;
    @Column(name = "starts_at", nullable = false) private Instant startDateTime;
    @Column(name = "ends_at", nullable = false) private Instant endDateTime;
    @Column(name = "vehicle_name", nullable = false, length = 240) private String vehicleName;
    @Column(name = "pickup_location", nullable = false, length = 300) private String pickupLocation;
    @Column(name = "dropoff_location", nullable = false, length = 300) private String dropoffLocation;
    @Column(name = "customer_note", length = 2000) private String customerNote;
    @Column(name = "internal_note", length = 2000) private String internalNote;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position ASC") private List<QuoteLineEntity> lines = new ArrayList<>();
    protected QuoteEntity() { }
    public QuoteEntity(BookingEntity booking, String reference, int version) {
        id = UUID.randomUUID(); this.booking = booking; this.reference = reference; this.version = version;
        customerId = booking.getCustomer().getId(); vehicleId = booking.getVehicle().getId();
        currency = booking.getCurrency(); startDateTime = booking.getStartDateTime(); endDateTime = booking.getEndDateTime();
        pickupLocation = booking.getPickupLocation(); dropoffLocation = booking.getDropoffLocation();
        vehicleName = booking.getVehicle().getBrand().getName() + " " + booking.getVehicle().getModel();
    }
    public void setDraft(QuoteRequest request, BigDecimal subtotal, BigDecimal discount, BigDecimal tax) {
        if (status != QuoteStatus.DRAFT) throw new QuoteException("QUOTE_IMMUTABLE", "Seul un brouillon peut être modifié.");
        validUntil = request.validUntil(); customerNote = request.customerNote(); internalNote = request.internalNote();
        this.subtotal = subtotal; discountAmount = discount; taxAmount = tax; totalAmount = subtotal.subtract(discount).add(tax);
    }
    public void transition(QuoteStatus target) {
        if (!(status == QuoteStatus.DRAFT && target == QuoteStatus.SENT || status == QuoteStatus.SENT &&
                Set.of(QuoteStatus.ACCEPTED, QuoteStatus.REJECTED, QuoteStatus.EXPIRED, QuoteStatus.CANCELLED).contains(target))) {
            throw new QuoteException("INVALID_QUOTE_TRANSITION", "Cette action n'est pas autorisée pour le statut du devis.");
        }
        status = target;
        if (target == QuoteStatus.SENT) sentAt = Instant.now();
        if (target == QuoteStatus.ACCEPTED) acceptedAt = Instant.now();
        if (target == QuoteStatus.REJECTED) rejectedAt = Instant.now();
    }
    public boolean coherentWith(BookingEntity candidate) {
        return customerId.equals(candidate.getCustomer().getId()) && vehicleId.equals(candidate.getVehicle().getId())
                && currency.equals(candidate.getCurrency()) && startDateTime.equals(candidate.getStartDateTime()) && endDateTime.equals(candidate.getEndDateTime())
                && pickupLocation.equals(candidate.getPickupLocation()) && dropoffLocation.equals(candidate.getDropoffLocation());
    }
    public UUID getId() { return id; } public BookingEntity getBooking() { return booking; }
    public UUID getCustomerId() { return customerId; } public UUID getVehicleId() { return vehicleId; }
    public String getReference() { return reference; } public int getVersion() { return version; } public QuoteStatus getStatus() { return status; }
    public String getCurrency() { return currency; } public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; } public BigDecimal getTaxAmount() { return taxAmount; } public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getValidUntil() { return validUntil; } public Instant getStartDateTime() { return startDateTime; } public Instant getEndDateTime() { return endDateTime; }
    public String getVehicleName() { return vehicleName; } public String getPickupLocation() { return pickupLocation; } public String getDropoffLocation() { return dropoffLocation; }
    public String getCustomerNote() { return customerNote; } public String getInternalNote() { return internalNote; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; } public Instant getAcceptedAt() { return acceptedAt; } public Instant getRejectedAt() { return rejectedAt; }
    public List<QuoteLineEntity> getLines() { return lines; }
}