package tn.elitetunisia.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import tn.elitetunisia.common.pricing.RentalPricingService;

class RentalPricingBoundaryTest {

    private final RentalPricingService pricing = new tn.elitetunisia.common.pricing.RentalPricingService();

    @Test
    void pricingAt24HourBoundaries() {
        Instant now = Instant.now().plusSeconds(60);
        // Ensure microsecond precision (truncate nanos to microseconds)
        now = now.truncatedTo(ChronoUnit.MICROS);

        Instant start = now;
        Instant justBefore24h = start.plus(24, ChronoUnit.HOURS).minusMillis(1);
        Instant exactly24h = start.plus(24, ChronoUnit.HOURS);
        Instant justAfter24h = start.plus(24, ChronoUnit.HOURS).plusMillis(1);
        Instant plus24h1ms = start.plus(24, ChronoUnit.HOURS).plusMillis(1);
        Instant plus48h1ms = start.plus(48, ChronoUnit.HOURS).plusMillis(1);

        BigDecimal pricePerDay = BigDecimal.valueOf(100);

        var p1 = pricing.calculate(pricePerDay, start, justBefore24h);
        var p2 = pricing.calculate(pricePerDay, start, exactly24h);
        var p3 = pricing.calculate(pricePerDay, start, justAfter24h);
        var p4 = pricing.calculate(pricePerDay, start, plus24h1ms);
        var p5 = pricing.calculate(pricePerDay, start, plus48h1ms);

        // Expectations depend on the project's pricing rules: ensure monotonicity and day-count changes
        assertTrue(p1.days() <= p2.days());
        assertTrue(p2.days() <= p3.days());
        assertTrue(p3.days() <= p4.days());
        assertTrue(p4.days() < p5.days());

        assertTrue(p1.amount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(p5.amount().compareTo(p1.amount()) > 0);
    }
}
