package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockGroupTest {

    @Test
    void normalizesKey() {
        StockGroup group = new StockGroup("  oil_gas ", "  Oil & Gas ", 50);

        assertThat(group.getKey()).isEqualTo("OIL_GAS");
        assertThat(group.getName()).isEqualTo("Oil & Gas");
        assertThat(group.getSortOrder()).isEqualTo(50);
    }

    @Test
    void rejectsKeyWithSpacesOrLeadingDigit() {
        assertThatThrownBy(() -> new StockGroup("OIL GAS", "x", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockGroup("1OIL", "x", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockGroup("A", "x", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockGroup(null, "x", 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new StockGroup("BANKS", "  ", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockGroup("BANKS", "x".repeat(101), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameAndMoveToChangeNameAndOrderButNotKey() {
        StockGroup group = new StockGroup("BANKS", "Banks", 10);

        group.rename(" Banking ");
        group.moveTo(15);

        assertThat(group.getKey()).isEqualTo("BANKS");
        assertThat(group.getName()).isEqualTo("Banking");
        assertThat(group.getSortOrder()).isEqualTo(15);
        assertThatThrownBy(() -> group.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReservedKeyNone() {
        assertThatThrownBy(() -> new StockGroup("none", "None", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }
}
