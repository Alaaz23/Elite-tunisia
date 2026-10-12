package tn.elitetunisia.driver;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

public class AdminDriverAssignmentControllerTest {
    @Test
    public void assignAndUnassignDirectController() {
        DriverAssignmentService svc = mock(DriverAssignmentService.class);
        AdminDriverAssignmentController ctrl = new AdminDriverAssignmentController(svc);
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingDriverAssignmentEntity a = new BookingDriverAssignmentEntity(bookingId, driverId, null, null);
        when(svc.assignDriver(bookingId, driverId, false)).thenReturn(a);
        AdminDriverAssignmentController.AssignDriverRequest req = new AdminDriverAssignmentController.AssignDriverRequest();
        req.driverId = driverId;
        var resp = ctrl.assign(bookingId, req);
        assertEquals(200, resp.getStatusCode().value());
        verify(svc).assignDriver(bookingId, driverId, false);

        doNothing().when(svc).unassignDriver(bookingId);
        var del = ctrl.unassign(bookingId);
        assertEquals(204, del.getStatusCode().value());
        verify(svc).unassignDriver(bookingId);
    }
}
