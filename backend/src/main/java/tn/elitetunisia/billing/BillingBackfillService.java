package tn.elitetunisia.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tn.elitetunisia.common.exception.DomainException;

/**
 * Régularisation administrative des réservations COMPLETED sans facture principale.
 * Jamais déclenchée automatiquement. Le dry-run ne fait aucune écriture et produit une empreinte ;
 * l'exécution exige cette empreinte (confirmation explicite), un verrou unique et réutilise le chemin outbox
 * ({@link BillingRequestService}) : idempotente et reprenable.
 */
@Service
public class BillingBackfillService {
    private static final Logger LOG = LoggerFactory.getLogger(BillingBackfillService.class);
    /** Un verrou plus ancien que ce délai est considéré abandonné (processus interrompu). */
    static final Duration STALE_LOCK = Duration.ofMinutes(30);

    public enum Reason {
        ELIGIBLE, ALREADY_INVOICED, NOT_COMPLETED, DEAD_REQUEST, NO_RETURN, NO_ACCEPTED_QUOTE,
        MISSING_CUSTOMER_IDENTITY, MISSING_VEHICLE, AMOUNT_NOT_COMPUTABLE
    }

    public record Line(UUID bookingId, String reference, String detail) { }
    public record Created(UUID bookingId, String reference, UUID invoiceId) { }
    public record Report(boolean dryRun, String fingerprint, List<Line> eligible, List<Line> skipped,
            List<Created> created, List<Line> failed) { }

    private record Candidate(UUID bookingId, String reference, String status, boolean hasReturn, boolean hasAcceptedQuote,
            boolean hasCustomerIdentity, boolean hasVehicle, boolean hasAmount, boolean hasMainInvoice, boolean hasDeadRequest) { }

    private static final String CANDIDATES_SQL = """
            select b.id, b.reference, b.status,
                   exists (select 1 from booking_operations o where o.booking_id = b.id and o.action = 'RETURN') as has_return,
                   exists (select 1 from quotes q where q.booking_id = b.id and q.status = 'ACCEPTED') as has_accepted_quote,
                   exists (select 1 from customers c where c.id = b.customer_id
                           and trim(coalesce(c.first_name, '')) <> '' and trim(coalesce(c.last_name, '')) <> '') as has_customer_identity,
                   exists (select 1 from vehicles v where v.id = b.vehicle_id) as has_vehicle,
                   exists (select 1 from quotes q where q.booking_id = b.id and q.status = 'ACCEPTED'
                           and q.currency is not null and q.total_amount is not null and q.total_amount >= 0) as has_amount,
                   exists (select 1 from invoices i where i.booking_id = b.id and i.quote_id is not null) as has_main_invoice,
                   exists (select 1 from billing_requests r where r.booking_id = b.id and r.kind = 'MAIN_INVOICE' and r.status = 'DEAD') as has_dead_request
            from bookings b
            order by b.reference
            """;

    private final JdbcTemplate jdbc;
    private final BillingRequestService billing;

    public BillingBackfillService(JdbcTemplate jdbc, BillingRequestService billing) {
        this.jdbc = jdbc; this.billing = billing;
    }

    /** Simulation : rapport des réservations éligibles et ignorées, sans aucune écriture. */
    public Report dryRun() {
        List<Line> eligible = new ArrayList<>();
        List<Line> skipped = new ArrayList<>();
        for (var candidate : candidates()) {
            Reason reason = classify(candidate);
            if (reason == Reason.ELIGIBLE) eligible.add(new Line(candidate.bookingId(), candidate.reference(), reason.name()));
            else skipped.add(new Line(candidate.bookingId(), candidate.reference(), reason.name()));
        }
        return new Report(true, fingerprint(eligible), eligible, skipped, List.of(), List.of());
    }

    /**
     * Exécution : seules les réservations de l'empreinte confirmée sont traitées (409 si la situation a changé
     * depuis le dry-run). Une seule exécution à la fois (409 sinon). Chaque création est tracée dans l'historique.
     */
    public Report execute(UUID actorId, String confirmedFingerprint) {
        if (confirmedFingerprint == null || confirmedFingerprint.isBlank()) throw DomainException.invalid("BACKFILL_FINGERPRINT_REQUIRED");
        Report preview = dryRun();
        if (!preview.fingerprint().equals(confirmedFingerprint.trim())) throw DomainException.conflict("BACKFILL_FINGERPRINT_MISMATCH");

        UUID runId = UUID.randomUUID();
        acquireRun(runId, actorId);
        List<Created> created = new ArrayList<>();
        List<Line> failed = new ArrayList<>();
        String outcome = "FAILED";
        String error = null;
        try {
            for (var line : preview.eligible()) {
                if (!heartbeat(runId)) {
                    outcome = "ABORTED";
                    error = "BACKFILL_LOCK_LOST";
                    break;
                }
                try {
                    UUID requestId = billing.enqueueMainInvoice(line.bookingId(), actorId).getId();
                    var result = billing.process(requestId);
                    var request = billing.get(requestId);
                    switch (result) {
                        case DONE -> {
                            created.add(new Created(line.bookingId(), line.reference(), request.getInvoiceId()));
                            LOG.info("Backfill: main invoice {} created for booking {} ({})", request.getInvoiceId(), line.reference(), line.bookingId());
                        }
                        case SKIPPED -> preview.skipped().add(new Line(line.bookingId(), line.reference(), "ALREADY_PROCESSED"));
                        default -> failed.add(new Line(line.bookingId(), line.reference(), result + ": " + request.getLastError()));
                    }
                } catch (RuntimeException ex) {
                    LOG.warn("Backfill failed for booking {}", line.reference(), ex);
                    failed.add(new Line(line.bookingId(), line.reference(), ex.getClass().getSimpleName()));
                }
            }
            if (!"ABORTED".equals(outcome)) outcome = "COMPLETED";
        } catch (RuntimeException ex) {
            error = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            finishRun(runId, outcome, error);
            releaseRun(runId);
        }
        if ("ABORTED".equals(outcome)) throw DomainException.conflict("BACKFILL_LOCK_LOST");
        return new Report(false, preview.fingerprint(), preview.eligible(), preview.skipped(), created, failed);
    }

    /**
     * Prise du verrou en une seule instruction conditionnelle : verrou libre, ou heartbeat expiré (exécution
     * interrompue). La reprise d'un verrou expiré est tracée (run précédent marqué TAKEN_OVER).
     */
    private void acquireRun(UUID runId, UUID actorId) {
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        List<UUID> previous = jdbc.query("""
                with previous as (select run_id from billing_backfill_lock where id = 1)
                update billing_backfill_lock l set run_id = ?, actor_id = ?, started_at = ?, heartbeat_at = ?
                from previous p
                where l.id = 1 and (l.run_id is null or l.heartbeat_at < ? or (l.heartbeat_at is null and l.started_at < ?))
                returning p.run_id
                """, (rs, rowNum) -> rs.getObject("run_id", UUID.class),
                runId, actorId, ts, ts, Timestamp.from(now.minus(STALE_LOCK)), Timestamp.from(now.minus(STALE_LOCK)));
        if (previous.isEmpty()) throw DomainException.conflict("BACKFILL_ALREADY_RUNNING");
        UUID tookOver = previous.get(0);
        try {
            if (tookOver != null) {
                jdbc.update("update billing_backfill_runs set status = 'TAKEN_OVER', finished_at = ?, heartbeat_at = ? where run_id = ? and status = 'RUNNING'",
                        ts, ts, tookOver);
                LOG.warn("Backfill lock expired: run {} taken over by run {} (actor {})", tookOver, runId, actorId);
            }
            jdbc.update("""
                    insert into billing_backfill_runs (run_id, actor_id, status, started_at, heartbeat_at, took_over_run_id)
                    values (?, ?, 'RUNNING', ?, ?, ?)
                    """, runId, actorId, ts, ts, tookOver);
        } catch (RuntimeException ex) {
            releaseRun(runId);
            throw ex;
        }
    }

    /** Renouvelle le heartbeat ; false si le verrou n'appartient plus à cette exécution. */
    private boolean heartbeat(UUID runId) {
        Timestamp ts = Timestamp.from(Instant.now());
        int renewed = jdbc.update("update billing_backfill_lock set heartbeat_at = ? where id = 1 and run_id = ?", ts, runId);
        if (renewed == 1) jdbc.update("update billing_backfill_runs set heartbeat_at = ? where run_id = ?", ts, runId);
        return renewed == 1;
    }

    private void finishRun(UUID runId, String outcome, String error) {
        Timestamp ts = Timestamp.from(Instant.now());
        jdbc.update("update billing_backfill_runs set status = ?, finished_at = ?, heartbeat_at = ?, error_message = ? where run_id = ? and status = 'RUNNING'",
                outcome, ts, ts, error, runId);
    }

    private void releaseRun(UUID runId) {
        jdbc.update("update billing_backfill_lock set run_id = null, actor_id = null, started_at = null, heartbeat_at = null where id = 1 and run_id = ?", runId);
    }

    /** Empreinte SHA-256 des réservations éligibles (triées) : l'exécution doit confirmer exactement ce lot. */
    static String fingerprint(List<Line> eligible) {
        String joined = eligible.stream().map(l -> l.bookingId().toString()).sorted().collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private List<Candidate> candidates() {
        return jdbc.query(CANDIDATES_SQL, (rs, rowNum) -> new Candidate(
                rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("status"),
                rs.getBoolean("has_return"), rs.getBoolean("has_accepted_quote"), rs.getBoolean("has_customer_identity"),
                rs.getBoolean("has_vehicle"), rs.getBoolean("has_amount"),
                rs.getBoolean("has_main_invoice"), rs.getBoolean("has_dead_request")));
    }

    /**
     * Ordre des motifs : une facture existante prime ; une réservation non terminée n'est jamais facturée ;
     * une facture approximative n'est jamais produite (montant et identité obligatoires).
     */
    private static Reason classify(Candidate c) {
        if (c.hasMainInvoice()) return Reason.ALREADY_INVOICED;
        if (!"COMPLETED".equals(c.status())) return Reason.NOT_COMPLETED;
        if (c.hasDeadRequest()) return Reason.DEAD_REQUEST;
        if (!c.hasReturn()) return Reason.NO_RETURN;
        if (!c.hasAcceptedQuote()) return Reason.NO_ACCEPTED_QUOTE;
        if (!c.hasCustomerIdentity()) return Reason.MISSING_CUSTOMER_IDENTITY;
        if (!c.hasVehicle()) return Reason.MISSING_VEHICLE;
        if (!c.hasAmount()) return Reason.AMOUNT_NOT_COMPUTABLE;
        return Reason.ELIGIBLE;
    }
}
