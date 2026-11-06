package com.valuescreener.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockGroupServiceTest {

    @Mock
    private StockGroupRepository groups;

    @Mock
    private CompanySnapshotRepository snapshots;

    private StockGroupService service;

    @BeforeEach
    void setUp() {
        service = new StockGroupService(groups, snapshots);
    }

    @Test
    void listIsSortedBySortOrderThenName() {
        when(groups.findAll()).thenReturn(List.of(
                new StockGroup("ZED", "Zed", 20),
                new StockGroup("BETA", "Beta", 20),
                new StockGroup("FIRST", "First", 10)));
        when(snapshots.countByStockGroup_Key(any())).thenReturn(3L);

        List<StockGroupView> views = service.list();

        assertThat(views).extracting(StockGroupView::key).containsExactly("FIRST", "BETA", "ZED");
        assertThat(views.get(0).stockCount()).isEqualTo(3L);
    }

    @Test
    void createAppendsAfterLastSortOrder() {
        when(groups.existsById("NEW_GROUP")).thenReturn(false);
        when(groups.existsByNameIgnoreCase("New Group")).thenReturn(false);
        when(groups.maxSortOrder()).thenReturn(350);
        when(groups.save(any(StockGroup.class))).thenAnswer(i -> i.getArgument(0));

        StockGroupView view = service.create(new CreateStockGroupRequest(" new_group ", "New Group"));

        assertThat(view.key()).isEqualTo("NEW_GROUP");
        assertThat(view.sortOrder()).isEqualTo(360);
        assertThat(view.stockCount()).isZero();
    }

    @Test
    void createRejectsDuplicateKeyOrNameCaseInsensitive() {
        when(groups.existsById("BANKS")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateStockGroupRequest("banks", "Other")))
                .isInstanceOf(StockGroupConflictException.class);

        when(groups.existsById("OTHER")).thenReturn(false);
        when(groups.existsByNameIgnoreCase("banks")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateStockGroupRequest("OTHER", "banks")))
                .isInstanceOf(StockGroupConflictException.class);
        verify(groups, never()).save(any());
    }

    @Test
    void updateRenamesAndKeepsKey() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(groups.existsByNameIgnoreCase("Banking")).thenReturn(false);
        when(snapshots.countByStockGroup_Key("BANKS")).thenReturn(2L);

        StockGroupView view = service.update("banks", new UpdateStockGroupRequest("Banking", 15));

        assertThat(view.key()).isEqualTo("BANKS");
        assertThat(view.name()).isEqualTo("Banking");
        assertThat(view.sortOrder()).isEqualTo(15);
        assertThat(view.stockCount()).isEqualTo(2L);
    }

    @Test
    void updateWithNullFieldsChangesNothingAndNameClashIsConflict() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByStockGroup_Key("BANKS")).thenReturn(0L);
        assertThat(service.update("BANKS", new UpdateStockGroupRequest(null, null)).name()).isEqualTo("Banks");

        when(groups.existsByNameIgnoreCase("Insurance")).thenReturn(true);
        assertThatThrownBy(() -> service.update("BANKS", new UpdateStockGroupRequest("Insurance", null)))
                .isInstanceOf(StockGroupConflictException.class);
    }

    @Test
    void updateUnknownIsNotFound() {
        when(groups.findById("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("NOPE", new UpdateStockGroupRequest("x", null)))
                .isInstanceOf(StockGroupNotFoundException.class);
    }

    @Test
    void deleteUsedGroupIsConflict() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByStockGroup_Key("BANKS")).thenReturn(1L);

        assertThatThrownBy(() -> service.delete("BANKS")).isInstanceOf(StockGroupConflictException.class);
        verify(groups, never()).delete(any());
    }

    @Test
    void deleteUnusedGroupDeletesAndUnknownIsNotFound() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByStockGroup_Key("BANKS")).thenReturn(0L);
        service.delete("banks");
        verify(groups).delete(banks);

        when(groups.findById("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("NOPE")).isInstanceOf(StockGroupNotFoundException.class);
    }

    @Test
    void requireUnknownThrowsUnknownGroup() {
        when(groups.findById("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.require(" nope "))
                .isInstanceOf(UnknownGroupException.class)
                .hasMessage("Unknown group key: NOPE");
    }

    @Test
    void requireNormalizesKey() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        assertThat(service.require(" banks ")).isSameAs(banks);
    }
}
