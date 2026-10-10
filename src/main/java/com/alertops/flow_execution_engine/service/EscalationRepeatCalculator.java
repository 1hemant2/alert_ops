package com.alertops.flow_execution_engine.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.alertops.flow_execution_engine.model.RepeatType;

/** Calculates daily and weekly repeats from the original local calendar time. */
public class EscalationRepeatCalculator {

    // Returns the first repeat strictly after the supplied instant.
    public Instant nextAfter(Instant originalStart, String timezone, RepeatType repeatType, Instant after) {
        validateInputs(originalStart, timezone, repeatType, after);
        if (repeatType == RepeatType.NONE) {
            return null;
        }

        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime original = originalStart.atZone(zone);
        LocalDateTime candidate = original.toLocalDateTime();
        LocalDateTime localAfter = after.atZone(zone).toLocalDateTime();
        long daysFromOriginal = ChronoUnit.DAYS.between(original.toLocalDate(), localAfter.toLocalDate());
        long interval = repeatType == RepeatType.DAILY ? 1 : 7;
        long periods = Math.max(0, Math.floorDiv(daysFromOriginal, interval));
        candidate = candidate.plusDays(periods * interval);

        Instant candidateInstant = resolve(candidate, zone);
        while (!candidateInstant.isAfter(after)) {
            candidate = candidate.plusDays(interval);
            candidateInstant = resolve(candidate, zone);
        }
        return candidateInstant;
    }

    // Returns the latest repeat at or before now, or empty before the first repeat.
    public Optional<Instant> latestAtOrBefore(
            Instant originalStart, String timezone, RepeatType repeatType, Instant now) {
        validateInputs(originalStart, timezone, repeatType, now);
        if (repeatType == RepeatType.NONE) {
            return Optional.empty();
        }

        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime original = originalStart.atZone(zone);
        LocalDateTime localNow = now.atZone(zone).toLocalDateTime();
        long daysFromOriginal = ChronoUnit.DAYS.between(original.toLocalDate(), localNow.toLocalDate());
        long interval = repeatType == RepeatType.DAILY ? 1 : 7;
        if (daysFromOriginal < interval) {
            return Optional.empty();
        }

        long periods = Math.floorDiv(daysFromOriginal, interval);
        LocalDateTime candidate = original.toLocalDateTime().plusDays(periods * interval);
        Instant candidateInstant = resolve(candidate, zone);
        while (candidateInstant.isAfter(now)) {
            candidate = candidate.minusDays(interval);
            candidateInstant = resolve(candidate, zone);
        }
        return candidate.toLocalDate().isBefore(original.toLocalDate())
                ? Optional.empty()
                : Optional.of(candidateInstant);
    }

    // Applies the fixed DST policy for future calendar occurrences.
    private Instant resolve(LocalDateTime localDateTime, ZoneId zone) {
        List<java.time.ZoneOffset> offsets = zone.getRules().getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            return localDateTime.atZone(zone).toInstant();
        }
        return localDateTime.toInstant(offsets.get(0));
    }

    // Rejects incomplete calculator inputs before applying a calendar rule.
    private void validateInputs(Instant originalStart, String timezone, RepeatType repeatType, Instant reference) {
        Objects.requireNonNull(originalStart, "originalStart");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(repeatType, "repeatType");
        Objects.requireNonNull(reference, "reference");
    }
}
