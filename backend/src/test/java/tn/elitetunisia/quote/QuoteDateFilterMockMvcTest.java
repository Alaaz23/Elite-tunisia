package tn.elitetunisia.quote;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tn.elitetunisia.PostgresTestHelper;

/** Binding HTTP des filtres de date de la liste admin des devis (createdFrom via {@code date}, {@code validUntil}). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuoteDateFilterMockMvcTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired MockMvc mvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor staff() {
        return SecurityMockMvcRequestPostProcessors.user("quote-date-admin@example.test")
                .authorities(new SimpleGrantedAuthority("admin:access"), new SimpleGrantedAuthority("quotes:read"));
    }

    @Test
    void emptyDateParametersAreIgnoredInsteadOfRejected() throws Exception {
        mvc.perform(get("/api/admin/quotes?date=&validUntil=").with(staff())).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void utcInstantFiltersAreAccepted() throws Exception {
        mvc.perform(get("/api/admin/quotes?date=2026-01-01T00:00:00Z&validUntil=2026-12-31T23:59:59.999999Z").with(staff()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void sameBoundsAreAcceptedAndReturnAnEmptyPageWhenNothingMatches() throws Exception {
        mvc.perform(get("/api/admin/quotes?date=2026-01-01T00:00:00Z&validUntil=2026-01-01T00:00:00Z").with(staff()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidDateIsRejectedWithBadRequest() throws Exception {
        mvc.perform(get("/api/admin/quotes?date=pas-une-date").with(staff())).andExpect(status().isBadRequest());
    }
}
