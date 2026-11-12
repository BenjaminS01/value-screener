package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentThesisTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-12T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private static InvestmentThesis empty() {
        return new InvestmentThesis("US0378331005", T0);
    }

    @Test
    void startsEmpty() {
        InvestmentThesis thesis = empty();

        assertThat(thesis.getIsin()).isEqualTo("US0378331005");
        assertThat(thesis.getWhyInteresting()).isNull();
        assertThat(thesis.getStatus()).isNull();
        assertThat(thesis.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void replaceSetsAllFieldsAndUpdatedAt() {
        InvestmentThesis thesis = empty();

        thesis.replace("why", "expected", "exit", ThesisStatus.WATCHING, "too expensive", T1);

        assertThat(thesis.getWhyInteresting()).isEqualTo("why");
        assertThat(thesis.getExpectedDevelopment()).isEqualTo("expected");
        assertThat(thesis.getExitCriteria()).isEqualTo("exit");
        assertThat(thesis.getStatus()).isEqualTo(ThesisStatus.WATCHING);
        assertThat(thesis.getStatusReason()).isEqualTo("too expensive");
        assertThat(thesis.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void blankValuesAreStoredAsNull() {
        InvestmentThesis thesis = empty();

        thesis.replace("  ", "", "\n", null, null, T1);

        assertThat(thesis.getWhyInteresting()).isNull();
        assertThat(thesis.getExpectedDevelopment()).isNull();
        assertThat(thesis.getExitCriteria()).isNull();
    }

    @Test
    void statusReasonWithoutStatusIsRejected() {
        InvestmentThesis thesis = empty();

        assertThatThrownBy(() -> thesis.replace("a", "b", "c", null, "reason", T1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("statusReason requires status");
    }

    @Test
    void blankStatusReasonWithoutStatusIsFine() {
        InvestmentThesis thesis = empty();

        thesis.replace("a", "b", "c", null, "  ", T1);

        assertThat(thesis.getStatusReason()).isNull();
    }

    @Test
    void statusReasonLongerThan500IsRejected() {
        InvestmentThesis thesis = empty();

        assertThatThrownBy(() -> thesis.replace("a", "b", "c", ThesisStatus.REJECTED, "x".repeat(501), T1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textsUpTo4000AreAcceptedAndLongerRejected() {
        InvestmentThesis thesis = empty();
        thesis.replace("x".repeat(4000), "x".repeat(4000), "x".repeat(4000), null, null, T1);

        assertThatThrownBy(() -> thesis.replace("x".repeat(4001), null, null, null, null, T1))
                .isInstanceOf(ThesisTooLongException.class).hasMessage("Thesis text too long");
        assertThatThrownBy(() -> thesis.replace(null, "x".repeat(4001), null, null, null, T1))
                .isInstanceOf(ThesisTooLongException.class);
        assertThatThrownBy(() -> thesis.replace(null, null, "x".repeat(4001), null, null, T1))
                .isInstanceOf(ThesisTooLongException.class);
    }

    @Test
    void appendToEmptyFieldHasNoLeadingBlankLines() {
        InvestmentThesis thesis = empty();

        thesis.appendPurchaseReason("Moat intact", DAY, T1);

        assertThat(thesis.getWhyInteresting()).isEqualTo("[2026-11-12] Kauf: Moat intact");
        assertThat(thesis.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void appendKeepsExistingTextAndAddsParagraph() {
        InvestmentThesis thesis = empty();
        thesis.replace("Cheap and solid.", null, null, null, null, T0);

        thesis.appendPurchaseReason("Moat intact", DAY, T1);

        assertThat(thesis.getWhyInteresting())
                .isEqualTo("Cheap and solid.\n\n[2026-11-12] Kauf: Moat intact");
    }

    @Test
    void appendBeyond4000ThrowsAndLeavesThesisUnchanged() {
        InvestmentThesis thesis = empty();
        thesis.replace("x".repeat(3990), null, null, null, null, T0);

        assertThatThrownBy(() -> thesis.appendPurchaseReason("Moat intact", DAY, T1))
                .isInstanceOf(ThesisTooLongException.class).hasMessage("Thesis text too long");

        assertThat(thesis.getWhyInteresting()).hasSize(3990);
        assertThat(thesis.getUpdatedAt()).isEqualTo(T0);
    }
}
