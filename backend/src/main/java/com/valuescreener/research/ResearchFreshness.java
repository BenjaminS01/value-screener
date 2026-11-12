package com.valuescreener.research;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/** Knows "today" (Clock) and the stale threshold (research.stale-after) and judges research dates. */
@Component
public class ResearchFreshness {

    private final Clock clock;
    private final Period staleAfter;

    public ResearchFreshness(Clock clock, @Value("${research.stale-after:P9M}") Period staleAfter) {
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    /** Dates strictly before this day are stale. */
    public LocalDate staleBefore() {
        return LocalDate.now(clock).minus(staleAfter);
    }

    public boolean isStale(LocalDate date) {
        return date == null || date.isBefore(staleBefore());
    }
}
