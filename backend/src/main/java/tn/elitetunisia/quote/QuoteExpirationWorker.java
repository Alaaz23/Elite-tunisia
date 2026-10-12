package tn.elitetunisia.quote;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
@Component @EnableScheduling
public class QuoteExpirationWorker {
    private final QuoteRepository quotes; private final QuoteService service;
    public QuoteExpirationWorker(QuoteRepository quotes, QuoteService service) { this.quotes = quotes; this.service = service; }
    @Scheduled(fixedDelayString = "${app.quotes.expiration-scan-ms:60000}", initialDelayString = "${app.quotes.expiration-scan-ms:60000}")
    public void expireDue() {
        for (var id : quotes.expiredIds(QuoteStatus.SENT, Instant.now(), PageRequest.of(0, 100))) {
            try { service.expire(id); } catch (RuntimeException exception) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Quote expiration failed for {}", id, exception); }
        }
    }
}