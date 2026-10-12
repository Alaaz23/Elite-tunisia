package tn.elitetunisia.transfer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TransferServiceTest {
    @Test
    public void entityBasics() {
        var t = new TransferEntity();
        t.setPickupAddress("Airport"); t.setDropoffAddress("Hotel"); t.setPassengers(2);
        assertEquals("Airport", t.getPickupAddress());
        assertEquals("Hotel", t.getDropoffAddress());
        assertEquals(2, t.getPassengers());
    }
}
