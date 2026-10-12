package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class DriverServiceTest {
    @Test
    public void entityBasics() {
        var d = new DriverEntity("Ali","Ben","+21612345678");
        assertEquals("Ali", d.getFirstName());
        assertEquals("Ben", d.getLastName());
        assertTrue(d.isActive());
        d.setActive(false);
        assertFalse(d.isActive());
    }
}
