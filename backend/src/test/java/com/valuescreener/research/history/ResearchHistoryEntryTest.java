package com.valuescreener.research.history;

import com.valuescreener.research.snapshot.ResearchCriterion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules that used to be CHECK / NOT NULL constraints of the history tables, now enforced before writing. */
class ResearchHistoryEntryTest {

    private static final Instant AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final ReplacedFinding REPLACED = new ReplacedFinding(ResearchCriterion.PE_RATIO,
            new BigDecimal("1.5"), null, "c", null, LocalDate.of(2026, 8, 1));

    @Test
    void findingHistoryRequiresOriginTimestampAndSnapshot() {
        assertThatThrownBy(() -> new ResearchFindingHistory("US0378331005", REPLACED, AT, null))
                .isInstanceOf(NullPointerException.class).hasMessage("origin must not be null");
        assertThatThrownBy(() -> new ResearchFindingHistory("US0378331005", REPLACED, null, HistoryOrigin.SYNC))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResearchFindingHistory(null, REPLACED, AT, HistoryOrigin.SYNC))
                .isInstanceOf(NullPointerException.class);
    }
}
