package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class DriverEntityFieldsTest {
    @Test
    public void licenseAndCertificationAccessors() {
        var d = new DriverEntity("John","Doe","+21600000000");
        d.setLicenseNumber("TN-12345");
        var exp = Instant.now().plusSeconds(3600);
        d.setLicenseExpiry(exp);
        d.setCertified(true);
        d.setNotes("Test notes");
        assertEquals("TN-12345", d.getLicenseNumber());
        assertEquals(exp, d.getLicenseExpiry());
        assertTrue(d.isCertified());
        assertEquals("Test notes", d.getNotes());
    }
}
