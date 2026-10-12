package tn.elitetunisia.common.pricing;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
@Service
public class RentalPricingService {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.999");
    public RentalPrice calculate(BigDecimal dailyRate, Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start) || dailyRate == null || dailyRate.signum() <= 0) throw new IllegalArgumentException("Invalid rental pricing input");
        Duration duration = Duration.between(start, end);
        long days = duration.toDays();
        if (!duration.equals(Duration.ofDays(days))) days++;
        BigDecimal amount = dailyRate.multiply(BigDecimal.valueOf(days)).setScale(3, java.math.RoundingMode.HALF_UP);
        if (days > Integer.MAX_VALUE || amount.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Rental price exceeds storage limits");
        return new RentalPrice((int) days, dailyRate, amount);
    }
    public record RentalPrice(int days, BigDecimal dailyRate, BigDecimal amount) { }
}