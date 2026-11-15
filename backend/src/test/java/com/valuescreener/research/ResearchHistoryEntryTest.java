package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules that used to be CHECK / NOT NULL constraints of the history tables, now enforced before writing. */
class ResearchHistoryEntryTest {

    private static final Instant AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final ReplacedFinding REPLACED = new ReplacedFinding(ResearchCriterion.PE_RATIO,
            new BigDecimal("1.5"), null, "c", null, LocalDate.of(2026, 8, 1));

    @Test
    void groupHistoryRejectsPreviousKeyWithoutPreviousSource() {
        assertThatThrownBy(() -> new StockGroupHistory("US0378331005", "BANKS", null, "INSURANCE",
                GroupSource.MANUAL, AT, HistoryOrigin.MANUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("previousGroupKey and previousSource must both be set or both be empty");
    }

    @Test
    void groupHistoryRejectsPreviousSourceWithoutPreviousKey() {
        assertThatThrownBy(() -> new StockGroupHistory("US0378331005", null, GroupSource.AI, "INSURANCE",
                GroupSource.MANUAL, AT, HistoryOrigin.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupHistoryAcceptsFirstAssignmentWithoutPreviousValues() {
        StockGroupHistory entry = new StockGroupHistory("US0378331005", null, null, "BANKS", GroupSource.AI,
                AT, HistoryOrigin.INGEST);

        assertThat(entry.getPreviousGroupKey()).isNull();
        assertThat(entry.getNewGroupKey()).isEqualTo("BANKS");
    }

    @Test
    void groupHistoryRequiresNewGroupSourceAndOrigin() {
        assertThatThrownBy(() -> new StockGroupHistory("US0378331005", null, null, null, GroupSource.AI, AT,
                HistoryOrigin.INGEST)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockGroupHistory("US0378331005", null, null, "BANKS", null, AT,
                HistoryOrigin.INGEST)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockGroupHistory("US0378331005", null, null, "BANKS", GroupSource.AI, AT,
                null)).isInstanceOf(NullPointerException.class);
    }

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
