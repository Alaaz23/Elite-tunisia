package tn.elitetunisia.quote;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;

/**
 * Catalogue des services additionnels proposés dans les devis.
 * Règles : un nom actif est unique par devise ; un service n'est jamais supprimé (les devis le référencent),
 * il est désactivé ; modifier le tarif n'affecte que les lignes ajoutées ensuite (les lignes existantes gardent leur tarif).
 */
@Service
public class ServiceCatalogService {
    private final ConfiguredServiceRepository services;
    private final AuditService audit;

    public ServiceCatalogService(ConfiguredServiceRepository services, AuditService audit) { this.services = services; this.audit = audit; }

    @Transactional(readOnly = true)
    public Page<ConfiguredServiceEntity> list(String search, Boolean active, Pageable pageable) {
        String term = search == null ? "" : search.strip();
        return active == null ? services.search(term, pageable) : services.searchByActive(term, active, pageable);
    }

    @Transactional(readOnly = true)
    public ConfiguredServiceEntity get(UUID id) { return services.findById(id).orElseThrow(() -> DomainException.notFound("SERVICE_NOT_FOUND")); }

    @Transactional
    public ConfiguredServiceEntity create(ServiceCreateRequest request, UUID actor, String ip) {
        String name = request.name().strip();
        if (name.length() < 2) throw DomainException.invalid("SERVICE_NAME_TOO_SHORT");
        if (services.countActiveNamed(name, request.currency(), new UUID(0, 0)) > 0) throw DomainException.conflict("SERVICE_NAME_TAKEN");
        var saved = services.saveAndFlush(new ConfiguredServiceEntity(name, request.description().strip(), request.price(), request.currency()));
        audit.recordAudit(actor, "SERVICE_CREATED", "SERVICE", saved.getId(), ip, "SUCCESS");
        return saved;
    }

    @Transactional
    public ConfiguredServiceEntity update(UUID id, ServiceUpdateRequest request, UUID actor, String ip) {
        var service = get(id);
        String name = request.name().strip();
        if (name.length() < 2) throw DomainException.invalid("SERVICE_NAME_TOO_SHORT");
        if (service.isActive() && services.countActiveNamed(name, service.getCurrency(), service.getId()) > 0) throw DomainException.conflict("SERVICE_NAME_TAKEN");
        service.updateDetails(name, request.description().strip(), request.price());
        var saved = services.saveAndFlush(service);
        audit.recordAudit(actor, "SERVICE_UPDATED", "SERVICE", saved.getId(), ip, "SUCCESS");
        return saved;
    }

    @Transactional
    public ConfiguredServiceEntity setActive(UUID id, boolean active, UUID actor, String ip) {
        var service = get(id);
        if (service.isActive() == active) return service;
        if (active && services.countActiveNamed(service.getName(), service.getCurrency(), service.getId()) > 0) throw DomainException.conflict("SERVICE_NAME_TAKEN");
        service.setActive(active);
        var saved = services.saveAndFlush(service);
        audit.recordAudit(actor, active ? "SERVICE_ACTIVATED" : "SERVICE_DEACTIVATED", "SERVICE", saved.getId(), ip, "SUCCESS");
        return saved;
    }
}
