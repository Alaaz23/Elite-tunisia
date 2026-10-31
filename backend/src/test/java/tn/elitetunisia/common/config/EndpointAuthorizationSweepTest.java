package tn.elitetunisia.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Parcourt toutes les routes réellement déclarées sous /api/admin et /api/customer et vérifie que
 * l'accès est refusé avant toute logique métier pour : un anonyme, un client, et un employé qui n'a
 * que l'accès au back-office sans aucune permission fonctionnelle.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAuthorizationSweepTest {
    @Autowired MockMvc mvc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private record Route(HttpMethod method, String path) { }

    private List<Route> routes(String prefix) {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) continue;
            for (var pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(prefix)) continue;
                String concrete = pattern.replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
                var methods = info.getMethodsCondition().getMethods();
                for (var method : methods) routes.add(new Route(HttpMethod.valueOf(method.name()), concrete));
            }
        }
        return routes;
    }

    @Test
    void sweepCoversTheRealSurface() {
        assertTrue(routes("/api/admin/").size() > 40, "le balayage doit couvrir toutes les routes d'administration");
        assertTrue(routes("/api/customer/").size() > 5, "le balayage doit couvrir les routes client");
    }

    @Test
    void anonymousCannotReachAdminOrCustomerRoutes() throws Exception {
        List<String> leaks = new ArrayList<>();
        for (String prefix : List.of("/api/admin/", "/api/customer/")) {
            for (Route route : routes(prefix)) {
                int status = mvc.perform(request(route.method(), route.path()).with(csrf())).andReturn().getResponse().getStatus();
                if (status != 401) leaks.add(route.method() + " " + route.path() + " -> " + status);
            }
        }
        assertTrue(leaks.isEmpty(), "routes accessibles sans authentification : " + leaks);
    }

    @Test
    void customersCannotReachAdminRoutes() throws Exception {
        var customer = user("customer@example.test").authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                new SimpleGrantedAuthority("bookings:cancel"), new SimpleGrantedAuthority("quotes:read"), new SimpleGrantedAuthority("quotes:accept"));
        List<String> leaks = new ArrayList<>();
        for (Route route : routes("/api/admin/")) {
            int status = mvc.perform(request(route.method(), route.path()).with(csrf()).with(customer)).andReturn().getResponse().getStatus();
            if (status != 403) leaks.add(route.method() + " " + route.path() + " -> " + status);
        }
        assertTrue(leaks.isEmpty(), "routes d'administration accessibles à un client : " + leaks);
    }

    @Test
    void backOfficeAccessAloneGrantsNothingFunctional() throws Exception {
        var staff = user("staff@example.test").authorities(new SimpleGrantedAuthority("admin:access"));
        List<String> leaks = new ArrayList<>();
        for (Route route : routes("/api/admin/")) {
            int status = mvc.perform(request(route.method(), route.path()).with(csrf()).with(staff)).andReturn().getResponse().getStatus();
            // 400/500 : la désérialisation du corps précède @PreAuthorize ; seule une réussite prouverait un contournement.
            if (status < 400) leaks.add(route.method() + " " + route.path() + " -> " + status);
        }
        assertFalse(routes("/api/admin/").isEmpty());
        assertTrue(leaks.isEmpty(), "routes accessibles avec admin:access seul : " + leaks);
    }

    @Test
    void nonMultipartUploadIsABadRequestNotAServerError() throws Exception {
        var manager = user("images@example.test").authorities(new SimpleGrantedAuthority("admin:access"), new SimpleGrantedAuthority("vehicles:manage"));
        int status = mvc.perform(request(HttpMethod.POST, "/api/admin/uploads/images").with(csrf()).with(manager)).andReturn().getResponse().getStatus();
        assertTrue(status == 400, "statut obtenu : " + status);
    }

    /** Les règles d'URL n'exigent que admin:access : chaque handler doit déclarer sa permission fonctionnelle. */
    @Test
    void everyAdminHandlerDeclaresAFunctionalPermission() {
        List<String> missing = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (info.getPathPatternsCondition() == null) return;
            for (var pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/admin/")) continue;
                if (pattern.startsWith("/api/admin/transfers") || pattern.startsWith("/api/admin/services")) continue; // règles d'URL hasAllAuthorities
                boolean annotated = org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(handler.getMethod(), org.springframework.security.access.prepost.PreAuthorize.class)
                        || org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), org.springframework.security.access.prepost.PreAuthorize.class);
                if (!annotated) missing.add(info.getMethodsCondition() + " " + pattern);
            }
        });
        assertTrue(missing.isEmpty(), "handlers d'administration sans @PreAuthorize : " + missing);
    }
}
