package tn.elitetunisia.vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.category.CategoryEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(name = "model_year", nullable = false)
    private Integer year;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "price_per_day", nullable = false, precision = 12, scale = 3)
    private BigDecimal pricePerDay;

    @Column(name = "price_per_week", nullable = false, precision = 12, scale = 3)
    private BigDecimal pricePerWeek;

    @Column(name = "price_with_driver", precision = 12, scale = 3)
    private BigDecimal priceWithDriver;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Integer passengers;

    @Column(nullable = false)
    private Integer doors;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransmissionType transmission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuelType fuel;

    @Column(name = "with_driver_available", nullable = false)
    private boolean withDriverAvailable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleFeatureEntity> features = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VehicleEntity() { }

    public VehicleEntity(BrandEntity brand, CategoryEntity category, String model, Integer year, String description,
            BigDecimal pricePerDay, BigDecimal pricePerWeek, BigDecimal priceWithDriver, String currency,
            Integer passengers, Integer doors, TransmissionType transmission, FuelType fuel,
            boolean withDriverAvailable, VehicleStatus status) {
        this.id = UUID.randomUUID();
        update(brand, category, model, year, description, pricePerDay, pricePerWeek, priceWithDriver, currency,
                passengers, doors, transmission, fuel, withDriverAvailable);
        this.status = status;
    }

    public void update(BrandEntity brand, CategoryEntity category, String model, Integer year, String description,
            BigDecimal pricePerDay, BigDecimal pricePerWeek, BigDecimal priceWithDriver, String currency,
            Integer passengers, Integer doors, TransmissionType transmission, FuelType fuel, boolean withDriverAvailable) {
        this.brand = brand;
        this.category = category;
        this.model = model;
        this.year = year;
        this.description = description;
        this.pricePerDay = pricePerDay;
        this.pricePerWeek = pricePerWeek;
        this.priceWithDriver = priceWithDriver;
        this.currency = currency;
        this.passengers = passengers;
        this.doors = doors;
        this.transmission = transmission;
        this.fuel = fuel;
        this.withDriverAvailable = withDriverAvailable;
    }

    public void setStatus(VehicleStatus status) { this.status = status; }
    public void replaceFeatures(List<String> names) {
        features.clear();
        names.stream().distinct().forEach(name -> features.add(new VehicleFeatureEntity(this, name, features.size())));
    }
    public void addImage(VehicleImageEntity image) {
        if (image.isPrimary()) images.forEach(VehicleImageEntity::unsetPrimary);
        images.add(image);
    }
    public void replaceImages(List<VehicleImageRequest> replacements) {
        images.clear();
        replacements.forEach(image -> addImage(new VehicleImageEntity(this, image.url(), image.altText(), image.position(), image.isPrimary())));
    }
    public void removeImage(VehicleImageEntity image) { images.remove(image); }
    public UUID getId() { return id; }
    public BrandEntity getBrand() { return brand; }
    public CategoryEntity getCategory() { return category; }
    public String getModel() { return model; }
    public Integer getYear() { return year; }
    public String getDescription() { return description; }
    public BigDecimal getPricePerDay() { return pricePerDay; }
    public BigDecimal getPricePerWeek() { return pricePerWeek; }
    public BigDecimal getPriceWithDriver() { return priceWithDriver; }
    public String getCurrency() { return currency; }
    public Integer getPassengers() { return passengers; }
    public Integer getDoors() { return doors; }
    public TransmissionType getTransmission() { return transmission; }
    public FuelType getFuel() { return fuel; }
    public boolean isWithDriverAvailable() { return withDriverAvailable; }
    public VehicleStatus getStatus() { return status; }
    public List<VehicleImageEntity> getImages() { return images; }
    public List<VehicleFeatureEntity> getFeatures() { return features; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}