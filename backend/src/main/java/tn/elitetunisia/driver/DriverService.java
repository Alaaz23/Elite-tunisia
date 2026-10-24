package tn.elitetunisia.driver;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {
    private final DriverRepository repo;

    public DriverService(DriverRepository repo) { this.repo = repo; }

    public DriverEntity create(String firstName, String lastName, String phone, String licenseNumber) {
        return create(firstName, lastName, phone, licenseNumber, null, false, null);
    }

    public DriverEntity create(String firstName, String lastName, String phone, String licenseNumber,
            Instant licenseExpiry, boolean certified, String notes) {
        var driver = new DriverEntity(firstName, lastName, phone);
        driver.setLicenseNumber(licenseNumber);
        driver.setLicenseExpiry(licenseExpiry);
        driver.setCertified(certified);
        driver.setNotes(notes);
        return repo.save(driver);
    }

    public List<DriverEntity> listAvailable() { return repo.findByActiveTrue(); }

    public DriverEntity get(UUID id) { return repo.findById(id).orElseThrow(() -> new RuntimeException("DRIVER_NOT_FOUND")); }

    @Transactional
    public void deactivate(UUID id) { var d = get(id); d.setActive(false); repo.save(d); }
}
