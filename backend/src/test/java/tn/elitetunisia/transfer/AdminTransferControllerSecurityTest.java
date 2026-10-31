package tn.elitetunisia.transfer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Les transferts ne doivent être accessibles qu'aux comptes autorisés à gérer les réservations. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminTransferControllerSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/admin/transfers")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"admin:access", "bookings:read"})
    void staffWithoutBookingManagementCannotListTransfers() throws Exception {
        mvc.perform(get("/api/admin/transfers")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"admin:access", "bookings:read"})
    void staffWithoutBookingManagementCannotCreateTransfers() throws Exception {
        mvc.perform(post("/api/admin/transfers").with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pickupAddress\":\"Aeroport\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"bookings:manage"})
    void bookingManagersWithoutBackOfficeAccessAreStillRejected() throws Exception {
        mvc.perform(get("/api/admin/transfers")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"admin:access", "bookings:manage"})
    void bookingManagersCanListTransfers() throws Exception {
        mvc.perform(get("/api/admin/transfers")).andExpect(status().isOk());
    }
}
