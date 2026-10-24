package tn.elitetunisia.booking;

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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@Import(AdminBookingOperationsControllerMockConfig.class)
@AutoConfigureMockMvc
public class AdminBookingOperationsControllerMockMvcTest {
    @Autowired MockMvc mvc;

    @Test
    public void unauthenticatedIsForbidden() throws Exception {
        mvc.perform(post("/api/admin/bookings/" + UUID.randomUUID() + "/pickup").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void adminCanRecordPickupAndReturn() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/admin/bookings/" + id + "/pickup").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("bookings:manage"), new org.springframework.security.core.authority.SimpleGrantedAuthority("admin:access"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"odometerStart\":1000,\"fuelStart\":80}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/admin/bookings/" + id + "/return").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("bookings:manage"), new org.springframework.security.core.authority.SimpleGrantedAuthority("admin:access"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"odometerEnd\":1200,\"fuelEnd\":70,\"damages\":null,\"extraCharges\":0}"))
            .andExpect(status().isOk());
    }
}

@TestConfiguration
class AdminBookingOperationsControllerMockConfig {
    @Bean
    public BookingOperationService bookingOperationService() {
        var service = org.mockito.Mockito.mock(BookingOperationService.class);
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        org.mockito.Mockito.when(service.recordPickup(org.mockito.ArgumentMatchers.any(java.util.UUID.class),
                org.mockito.ArgumentMatchers.any(java.util.UUID.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new BookingOperationEntity(id, actor, "PICKUP", 1000, null, 80, null, null, java.math.BigDecimal.ZERO));
        org.mockito.Mockito.when(service.recordReturn(org.mockito.ArgumentMatchers.any(java.util.UUID.class),
                org.mockito.ArgumentMatchers.any(java.util.UUID.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BookingOperationEntity(id, actor, "RETURN", null, 1200, null, 70, null, java.math.BigDecimal.ZERO));
        org.mockito.Mockito.when(service.list(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        return service;
    }
    @Bean
    public tn.elitetunisia.user.service.AccountService accountService() {
        var a = org.mockito.Mockito.mock(tn.elitetunisia.user.service.AccountService.class);
        var acc = new tn.elitetunisia.user.dto.UserSummary(UUID.randomUUID(), "Admin", "User", "admin@example.test", null, null, java.util.Set.of("ADMIN"), "ACTIVE");
        org.mockito.Mockito.when(a.current(org.mockito.Mockito.anyString())).thenReturn(acc);
        return a;
    }
}
