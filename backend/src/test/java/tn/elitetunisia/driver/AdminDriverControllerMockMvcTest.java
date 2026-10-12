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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@Import(AdminDriverControllerMockConfig.class)
@AutoConfigureMockMvc
public class AdminDriverControllerMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired DriverService driverService;

    @Test
    public void unauthorizedWithoutAuthority() throws Exception {
        mvc.perform(post("/api/admin/drivers").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"drivers:manage","admin:access"})
    public void createAllowedWithAuthority() throws Exception {
        mvc.perform(post("/api/admin/drivers").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"A\",\"lastName\":\"B\",\"phone\":\"+216...\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"other:scope"})
    public void forbiddenWithWrongAuthority() throws Exception {
        mvc.perform(post("/api/admin/drivers").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }
}

@TestConfiguration
class AdminDriverControllerMockConfig {
    @Bean
    public DriverService driverService() {
        return org.mockito.Mockito.mock(DriverService.class);
    }
}
