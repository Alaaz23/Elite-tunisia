package tn.elitetunisia.common.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAudit(UUID actorId, String action, String entityType, UUID entityId, String ipAddress, String result) {
        UserEntity actor = actorId == null ? null : users.findById(actorId).orElse(null);
        logs.save(new AuditLogEntity(actor, action, entityType, entityId, normalizeIp(ipAddress), result));
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return null;
        return ipAddress.length() <= 45 ? ipAddress : ipAddress.substring(0, 45);
    }
}