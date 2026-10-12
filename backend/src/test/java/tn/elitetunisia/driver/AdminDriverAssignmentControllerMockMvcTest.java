package tn.elitetunisia.driver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;

@SpringBootTest
@Import(AdminDriverAssignmentControllerMockConfig.class)
@AutoConfigureMockMvc
public class AdminDriverAssignmentControllerMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired DriverAssignmentService svc;

    @Test
    public void unauthenticatedGetsUnauthorized() throws Exception {
        // POST without authentication + no CSRF typically yields 403 (CSRF) when anonymous access is denied.
        mvc.perform(post("/api/admin/bookings/" + UUID.randomUUID() + "/driver").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void customerGetsForbidden() throws Exception {
        mvc.perform(post("/api/admin/bookings/" + UUID.randomUUID() + "/driver").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user@example.test").roles("CUSTOMER")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":\"00000000-0000-0000-0000-000000000000\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void adminWithAuthorityCanAssignAndUnassign() throws Exception {
        UUID booking = UUID.randomUUID(); UUID driver = UUID.randomUUID();
        when(svc.assignDriver(eq(booking), eq(driver), eq(false))).thenReturn(new BookingDriverAssignmentEntity(booking, driver, null, null));

        mvc.perform(post("/api/admin/bookings/" + booking + "/driver").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@example.test").authorities(new SimpleGrantedAuthority("bookings:manage"), new SimpleGrantedAuthority("admin:access"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":\"" + driver + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.driverId").value(driver.toString()));

        doNothing().when(svc).unassignDriver(eq(booking));
        mvc.perform(delete("/api/admin/bookings/" + booking + "/driver").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@example.test").authorities(new SimpleGrantedAuthority("bookings:manage"), new SimpleGrantedAuthority("admin:access"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isNoContent());
    }
}

@TestConfiguration
class AdminDriverAssignmentControllerMockConfig {
    @Bean
    public DriverAssignmentService driverAssignmentService() {
        return org.mockito.Mockito.mock(DriverAssignmentService.class);
    }
}
