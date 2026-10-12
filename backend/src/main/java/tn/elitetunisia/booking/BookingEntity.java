package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.elitetunisia.customer.CustomerEntity;
import tn.elitetunisia.vehicle.VehicleEntity;

@Entity
@Table(name = "bookings")
public class BookingEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String reference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private CustomerEntity customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehicle_id") private VehicleEntity vehicle;
    @Column(name = "starts_at", nullable = false) private Instant startDateTime;
    @Column(name = "ends_at", nullable = false) private Instant endDateTime;
    @Column(name = "pickup_location", nullable = false, length = 300) private String pickupLocation;
    @Column(name = "dropoff_location", nullable = false, length = 300) private String dropoffLocation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BookingStatus status;
    @Column(name = "base_price", nullable = false, precision = 12, scale = 3) private BigDecimal basePrice;
    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 3) private BigDecimal dailyRate;
    @Column(name = "additional_charges", nullable = false, precision = 12, scale = 3) private BigDecimal additionalCharges = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal tax = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 3) private BigDecimal totalAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "number_of_days", nullable = false) private int numberOfDays;
    @Column(name = "customer_note", length = 2000) private String customerNote;
    @Column(name = "internal_note", length = 2000) private String internalNote;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;

    protected BookingEntity() { }
    public BookingEntity(String reference, CustomerEntity customer, VehicleEntity vehicle, BookingRequest request, BookingPreview price) {
        this.id = UUID.randomUUID(); this.reference = reference; this.customer = customer; this.vehicle = vehicle;
        this.startDateTime = request.startDateTime(); this.endDateTime = request.endDateTime();
        this.pickupLocation = request.pickupLocation().trim(); this.dropoffLocation = request.dropoffLocation().trim();
        this.customerNote = request.customerNote(); this.status = BookingStatus.PENDING;
        this.dailyRate = vehicle.getPricePerDay(); this.basePrice = price.basePrice(); this.totalAmount = price.totalAmount();
        this.currency = price.currency(); this.numberOfDays = price.numberOfDays();
    }
    public void transition(BookingStatus target, Instant now) {
        if (status == BookingStatus.CANCELLED && target == BookingStatus.CANCELLED) throw new BookingException("BOOKING_ALREADY_CANCELLED", "Cette réservation est déjà annulée.");
        if (!status.canTransitionTo(target)) throw new BookingException("INVALID_STATUS_TRANSITION", "Cette action n'est pas autorisée pour le statut actuel.");
        status = target;
        if (target == BookingStatus.CONFIRMED) confirmedAt = now;
        if (target == BookingStatus.CANCELLED) cancelledAt = now;
    }
    public void update(BookingUpdateRequest request) {
        pickupLocation = request.pickupLocation().trim(); dropoffLocation = request.dropoffLocation().trim(); internalNote = request.internalNote();
    }
    public void applyCommercialPrice(tn.elitetunisia.quote.QuoteEntity quote) {
        var rental = quote.getLines().stream().filter(line -> line.getType().equals("VEHICLE_RENTAL")).findFirst().orElseThrow();
        dailyRate = rental.getUnitPrice(); numberOfDays = rental.getQuantity(); basePrice = rental.getAmount();
        additionalCharges = quote.getSubtotal().subtract(basePrice); discount = quote.getDiscountAmount(); tax = quote.getTaxAmount(); totalAmount = quote.getTotalAmount();
    }
    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public CustomerEntity getCustomer() { return customer; }
    public VehicleEntity getVehicle() { return vehicle; }
    public Instant getStartDateTime() { return startDateTime; }
    public Instant getEndDateTime() { return endDateTime; }
    public String getPickupLocation() { return pickupLocation; }
    public String getDropoffLocation() { return dropoffLocation; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getAdditionalCharges() { return additionalCharges; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public int getNumberOfDays() { return numberOfDays; }
    public String getCustomerNote() { return customerNote; }
    public String getInternalNote() { return internalNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}