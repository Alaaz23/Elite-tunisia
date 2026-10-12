package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "transfers")
public class TransferEntity {
    @Id
    @GeneratedValue
    private UUID id;
    private Instant pickupAt;
    private String pickupAddress;
    private String dropoffAddress;
    private Integer passengers;
    private UUID driverId;
    private UUID vehicleId;
    private String status = "PENDING";

    public TransferEntity() {}
    public UUID getId() { return id; }
    public Instant getPickupAt() { return pickupAt; }
    public void setPickupAt(Instant t) { this.pickupAt = t; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String a) { this.pickupAddress = a; }
    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String a) { this.dropoffAddress = a; }
    public Integer getPassengers() { return passengers; }
    public void setPassengers(Integer p) { this.passengers = p; }
    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID d) { this.driverId = d; }
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID v) { this.vehicleId = v; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
}
