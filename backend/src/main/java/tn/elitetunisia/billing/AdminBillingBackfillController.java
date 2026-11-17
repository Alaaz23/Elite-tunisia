package tn.elitetunisia.billing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/billing/backfill")
@PreAuthorize("hasAuthority('billing:backfill')")
public class AdminBillingBackfillController {
    private final BillingBackfillService backfill;
    private final AccountService accounts;

    public AdminBillingBackfillController(BillingBackfillService backfill, AccountService accounts) {
        this.backfill = backfill; this.accounts = accounts;
    }

    @PostMapping("/dry-run")
    public BillingBackfillService.Report dryRun() {
        return backfill.dryRun();
    }

    /** Exige l'empreinte renvoyée par le dry-run : seul le lot confirmé est exécuté. */
    public record ExecuteRequest(String fingerprint) { }

    @PostMapping("/execute")
    public BillingBackfillService.Report execute(@RequestBody ExecuteRequest body, Authentication auth) {
        return backfill.execute(accounts.current(auth.getName()).id(), body == null ? null : body.fingerprint());
    }
}
