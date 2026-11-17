package tn.elitetunisia.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.common.config.TestProfileAdminSeeder;
import tn.elitetunisia.user.dto.CreateEmployeeRequest;
import tn.elitetunisia.user.dto.UserSummary;
import tn.elitetunisia.user.service.AdminUserService;

/** Pagination de la liste des utilisateurs : total réel, taille bornée et nouveau compte retrouvable en première page. */
@SpringBootTest
@Transactional
class AdminUserPaginationTest {
    @Autowired AdminUserService service;
    @Autowired UserRepository users;

    @Test
    void listReturnsRealTotalAndNewestAccountFirst() {
        UUID actor = users.findByEmailIgnoreCase(TestProfileAdminSeeder.EMAIL).orElseThrow().getId();
        UUID created = service.create(new CreateEmployeeRequest("Pagination", "Test",
                "pagination-" + UUID.randomUUID() + "@example.test", "+21612345678", "Passw0rd!Passw0rd", "AGENT", "Agent"),
                actor, "127.0.0.1").id();

        PagedResponse<UserSummary> firstPage = service.list(0, 1);

        assertEquals(1, firstPage.content().size());
        assertEquals(created, firstPage.content().get(0).id());
        assertEquals(users.count(), firstPage.totalElements());
        assertEquals(0, firstPage.page());
        assertEquals(1, firstPage.size());
        assertTrue(firstPage.totalPages() >= 1);
    }

    @Test
    void listCapsPageSizeAtOneHundred() {
        PagedResponse<UserSummary> page = service.list(0, 500);

        assertEquals(100, page.size());
        assertTrue(page.content().size() <= 100);
    }
}
