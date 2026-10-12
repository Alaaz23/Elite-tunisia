package tn.elitetunisia.quote;
import java.math.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.elitetunisia.common.pricing.RentalPricingService;
@Component
public class QuoteTaxPolicy {
    private final BigDecimal amount;
    private final String currency;
    public QuoteTaxPolicy(@Value("${app.quotes.default-tax-amount:0}") BigDecimal amount, @Value("${app.quotes.tax-currency:TND}") String currency) {
        this.amount = amount.setScale(3, RoundingMode.HALF_UP); this.currency = currency;
        if (this.amount.signum() < 0 || this.amount.compareTo(RentalPricingService.MAX_AMOUNT) > 0) throw new IllegalStateException("Invalid configured tax amount");
    }
    public BigDecimal amountFor(String currency) {
        if (amount.signum() > 0 && !this.currency.equals(currency)) throw new QuoteException("QUOTE_CURRENCY_MISMATCH", "La taxe configurée ne correspond pas à la devise du devis.");
        return amount;
    }
}