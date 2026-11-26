package com.valuescreener.research.group;

import com.valuescreener.research.snapshot.CompanySnapshotRepository;
import com.valuescreener.research.snapshot.GroupSource;
import com.valuescreener.research.snapshot.SnapshotIndexEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockGroupLookupTest {

    @Mock
    private CompanySnapshotRepository snapshots;

    @Mock
    private StockGroupRepository groups;

    private static SnapshotIndexEntry entry(String isin, String groupKey) {
        return new SnapshotIndexEntry(isin, "T", "Name", "USA", groupKey,
                groupKey == null ? null : GroupSource.AI, null);
    }

    @Test
    void groupsComeFromTheIndexAndOneGroupReadNeverFromSnapshotItems() {
        when(snapshots.findIndex()).thenReturn(List.of(
                entry("US0000000001", "BANKS"),
                entry("US0000000002", null),
                entry("US0000000003", "BANKS"),
                entry("US0000000004", "INSURANCE")));
        when(groups.findAll()).thenReturn(List.of(new StockGroup("BANKS", "Banks", 10),
                new StockGroup("INSURANCE", "Insurance", 20)));

        Map<String, StockGroupRef> result = new StockGroupLookup(snapshots, groups)
                .groupsByIsin(List.of("US0000000001", "US0000000002", "US0000000004", "US0000000099"));

        assertThat(result).containsOnlyKeys("US0000000001", "US0000000004");
        assertThat(result.get("US0000000001")).isEqualTo(new StockGroupRef("BANKS", "Banks"));
        assertThat(result.get("US0000000004")).isEqualTo(new StockGroupRef("INSURANCE", "Insurance"));
        verify(snapshots, times(1)).findIndex();
        verify(groups, times(1)).findAll();
        verify(snapshots, never()).findByIsin(any());
    }

    @Test
    void noIsinsMeansNoReads() {
        assertThat(new StockGroupLookup(snapshots, groups).groupsByIsin(List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(snapshots, groups);
    }
}
