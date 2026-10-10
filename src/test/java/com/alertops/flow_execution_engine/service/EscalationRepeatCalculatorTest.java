package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.alertops.flow_execution_engine.model.RepeatType;

class EscalationRepeatCalculatorTest {
    private final EscalationRepeatCalculator calculator = new EscalationRepeatCalculator();
    private final Instant originalStart = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    // Verifies daily repetition preserves the original local time.
    void calculatesTheNextDailyOccurrence() {
        Instant next = calculator.nextAfter(
                originalStart, "UTC", RepeatType.DAILY, originalStart);

        assertEquals(Instant.parse("2026-01-02T10:00:00Z"), next);
    }

    @Test
    // Verifies weekly repetition preserves the original weekday and local time.
    void calculatesTheNextWeeklyOccurrence() {
        Instant next = calculator.nextAfter(
                originalStart, "UTC", RepeatType.WEEKLY, originalStart);

        assertEquals(Instant.parse("2026-01-08T10:00:00Z"), next);
    }

    @Test
    // Verifies downtime creates only the latest daily occurrence.
    void findsTheLatestMissedOccurrence() {
        Optional<Instant> latest = calculator.latestAtOrBefore(
                originalStart,
                "UTC",
                RepeatType.DAILY,
                Instant.parse("2026-01-05T12:00:00Z"));

        assertTrue(latest.isPresent());
        assertEquals(Instant.parse("2026-01-05T10:00:00Z"), latest.get());
    }

    @Test
    // Verifies a daylight-saving gap is moved forward without changing the anchor.
    void resolvesFutureOccurrenceInsideDaylightSavingGap() {
        Instant original = Instant.parse("2026-03-07T02:30:00-05:00");
        Instant next = calculator.nextAfter(
                original, "America/New_York", RepeatType.DAILY, original.plusSeconds(23 * 3600));

        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next);
    }
}
