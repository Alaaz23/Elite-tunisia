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
    void everyStartedTwentyFourHourSliceIsBilled() {
        Instant start = Instant.parse("2026-01-10T08:00:00Z");
        BigDecimal dailyRate = BigDecimal.valueOf(100);
        assertEquals(1, pricing.calculate(dailyRate, start, start.plus(1, ChronoUnit.HOURS)).days(), "1 h");
        assertEquals(1, pricing.calculate(dailyRate, start, start.plus(23, ChronoUnit.HOURS)).days(), "23 h");
        assertEquals(1, pricing.calculate(dailyRate, start, start.plus(24, ChronoUnit.HOURS)).days(), "24 h exactes");
        assertEquals(2, pricing.calculate(dailyRate, start, start.plus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES)).days(), "24 h 1 min");
        assertEquals(2, pricing.calculate(dailyRate, start, start.plus(47, ChronoUnit.HOURS)).days(), "47 h");
        assertEquals(2, pricing.calculate(dailyRate, start, start.plus(48, ChronoUnit.HOURS)).days(), "48 h exactes");
        var threeSlices = pricing.calculate(dailyRate, start, start.plus(48, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES));
        assertEquals(3, threeSlices.days(), "48 h 1 min");
        assertEquals(new BigDecimal("300.000"), threeSlices.amount());
    }

    @Test
    void sliceCountIsIndependentOfLocalTimeZone() {
        Instant start = Instant.parse("2026-03-28T22:30:00Z");
        Instant end = start.plus(25, ChronoUnit.HOURS);
        assertEquals(2, pricing.calculate(BigDecimal.TEN, start, end).days());
    }
}
