package tn.elitetunisia.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCustomerControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void customerDirectoryRequiresPermission() throws Exception {
        mvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"admin:access", "customers:read"})
    void customerDirectorySupportsSearchAndPagination() throws Exception {
        mvc.perform(get("/api/admin/customers")
                        .param("search", "nobody-matches-this")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = {"admin:access", "customers:read"})
    void customerDirectoryBoundsPageSize() throws Exception {
        mvc.perform(get("/api/admin/customers").param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
