package tn.elitetunisia.notification;
import java.util.UUID;
public interface NotificationService {
    void notifyQuoteStatus(UUID quoteId, String status);
}