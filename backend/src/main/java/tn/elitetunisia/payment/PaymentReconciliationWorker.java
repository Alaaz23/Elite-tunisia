package tn.elitetunisia.payment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planificateur de réconciliation des paiements PENDING anciens et UNKNOWN.
 * Désactivé par défaut ({@code app.payment.reconciliation.enabled=true} pour l'activer).
 * Le verrou advisory de session est tenu sur une connexion dédiée pendant toute l'exécution,
 * ce qui empêche deux instances de réconcilier simultanément ; il n'est jamais tenu dans une transaction.
 * Le worker ne déclenche jamais de capture : il ne fait que consulter le statut existant par clé.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment.reconciliation", name = "enabled", havingValue = "true")
public class PaymentReconciliationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationWorker.class);
    /** Clé de verrou advisory dédiée à la réconciliation des paiements (ne doit pas être réutilisée ailleurs). */
    static final long LOCK_KEY = 7_412_031_001L;

    public record RunReport(int processed, int errors, int skippedBecauseLocked) {}

    private final DataSource dataSource;
    private final PaymentReconciliationService reconciliation;
    private final Duration pendingOlderThan;
    private final int batchSize;

    public PaymentReconciliationWorker(DataSource dataSource, PaymentReconciliationService reconciliation,
                                       @Value("${app.payment.reconciliation.pending-older-than:PT15M}") Duration pendingOlderThan,
                                       @Value("${app.payment.reconciliation.batch-size:50}") int batchSize) {
        this.dataSource = dataSource;
        this.reconciliation = reconciliation;
        this.pendingOlderThan = pendingOlderThan;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.payment.reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${app.payment.reconciliation.initial-delay-ms:60000}")
    public void scheduledRun() {
        try {
            runOnce(Instant.now());
        } catch (RuntimeException | SQLException ex) {
            LOG.error("Réconciliation des paiements interrompue : {}", ex.getMessage(), ex);
        }
    }

    /**
     * Exécute un cycle de réconciliation. Un cycle concurrent déjà en cours est signalé par {@code skippedBecauseLocked=1}.
     */
    public RunReport runOnce(Instant now) throws SQLException {
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryAcquire(lockConnection)) {
                LOG.info("Réconciliation des paiements ignorée : un autre traitement la détient déjà.");
                return new RunReport(0, 0, 1);
            }
            try {
                return process(now);
            } finally {
                release(lockConnection);
            }
        }
    }

    private RunReport process(Instant now) {
        int processed = 0;
        int errors = 0;
        List<PaymentReconciliationService.Outcome> outcomes;
        try {
            outcomes = reconciliation.reconcileBatch(now.minus(pendingOlderThan), batchSize);
        } catch (RuntimeException ex) {
            LOG.error("Échec de la lecture du lot de réconciliation : {}", ex.getMessage(), ex);
            return new RunReport(0, 1, 0);
        }
        for (var outcome : outcomes) {
            processed++;
            if (outcome.detail().startsWith("ERROR:")) {
                errors++;
                LOG.error("Tentative {} : échec de réconciliation ({}), elle reste inchangée.", outcome.paymentId(), outcome.detail());
            } else if (outcome.detail().startsWith("SKIPPED")) {
                LOG.info("Tentative {} ignorée (verrouillée par un autre traitement).", outcome.paymentId());
            } else {
                LOG.info("Tentative {} réconciliée : {} -> {} ({})", outcome.paymentId(), outcome.before(), outcome.after(), outcome.detail());
            }
        }
        return new RunReport(processed, errors, 0);
    }

    private static boolean tryAcquire(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void release(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, LOCK_KEY);
            ps.execute();
        } catch (SQLException ex) {
            LOG.error("Libération du verrou de réconciliation impossible : {}", ex.getMessage(), ex);
        }
    }
}
