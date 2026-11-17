package tn.elitetunisia.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingRequestWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BillingRequestWorker.class);
    private final BillingRequestService service;

    public BillingRequestWorker(BillingRequestService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.billing.outbox-scan-ms:60000}",
            initialDelayString = "${app.billing.outbox-initial-delay-ms:30000}")
    public void processPendingInvoices() {
        for (var id : service.dueIds()) {
            try {
                var outcome = service.process(id);
                LOG.info("Billing request {} processed: {}", id, outcome);
            } catch (RuntimeException exception) {
                LOG.error("Billing request {} could not be processed", id, exception);
            }
        }
    }
}
