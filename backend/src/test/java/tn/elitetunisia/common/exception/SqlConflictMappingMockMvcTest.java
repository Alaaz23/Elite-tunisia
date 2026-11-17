package tn.elitetunisia.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SqlConflictMappingMockMvcTest {

    private static final String SECRET_MARKER = "secret_source_constraint";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void guaranteeOverlapIsMappedToGuaranteeOverlapCode() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "guarantee-overlap"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("GUARANTEE_OVERLAP"))
                .andExpect(jsonPath("$.detail").value("Une garantie active chevauche déjà cette période pour ce véhicule."))
                .andExpect(content().string(not(containsString("23P01"))))
                .andExpect(content().string(not(containsString(SECRET_MARKER))));
    }

    @Test
    void bookingOverlapFromTriggerIsMappedToVehicleNotAvailable() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "booking-overlap-trigger"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Ce véhicule est déjà réservé pour cette période."))
                .andExpect(content().string(not(containsString(SECRET_MARKER))));
    }

    @Test
    void bookingOverlapFromExclusionConstraintIsMappedToVehicleNotAvailable() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "booking-overlap-constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_AVAILABLE"))
                .andExpect(content().string(not(containsString(SECRET_MARKER))))
                .andExpect(content().string(not(containsString("bookings_no_blocking_overlap"))));
    }

    @Test
    void uncoveredGuaranteeIsMappedToVehicleNotAvailableWithoutRawMessage() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "guarantee-missing"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail").value("La garantie de cette réservation ne couvre plus la période demandée."))
                .andExpect(content().string(not(containsString(SECRET_MARKER))))
                .andExpect(content().string(not(containsString("23514"))));
    }

    @Test
    void unknownExclusionViolationIsGenericConflictWithoutCodeOrLeak() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "unknown-exclusion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.detail").value("Cette demande entre en conflit avec des données existantes."))
                .andExpect(content().string(not(containsString(SECRET_MARKER))))
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void unknownCheckViolationIsGenericConflictWithoutCodeOrLeak() throws Exception {
        mockMvc.perform(get("/test/sql-conflict").param("case", "unknown-check"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.detail").value("Cette demande entre en conflit avec des données existantes."))
                .andExpect(content().string(not(containsString(SECRET_MARKER))));
    }

    @RestController
    @RequestMapping("/test/sql-conflict")
    static class ConflictController {

        @GetMapping
        void conflict(@RequestParam("case") String scenario) {
            switch (scenario) {
                case "guarantee-overlap" -> throw sql("23P01",
                        "conflicting key value violates exclusion constraint: garantie chevauchante pour ce véhicule " + SECRET_MARKER);
                case "booking-overlap-trigger" -> throw sql("23P01",
                        "période déjà bloquée pour ce véhicule " + SECRET_MARKER);
                case "booking-overlap-constraint" -> throw sql("23P01",
                        "conflicting key value violates exclusion constraint \"bookings_no_blocking_overlap\" " + SECRET_MARKER);
                case "guarantee-missing" -> throw sql("23514",
                        "aucune garantie active ne couvre la réservation " + SECRET_MARKER);
                case "unknown-exclusion" -> throw sql("23P01",
                        "conflicting key value violates exclusion constraint \"" + SECRET_MARKER + "\"");
                case "unknown-check" -> throw sql("23514",
                        "new row violates check constraint \"" + SECRET_MARKER + "\"");
                default -> throw new IllegalArgumentException("unknown scenario " + scenario);
            }
        }

        private static DataIntegrityViolationException sql(String state, String message) {
            return new DataIntegrityViolationException("SQL failure", new SQLException(message, state));
        }
    }
}
