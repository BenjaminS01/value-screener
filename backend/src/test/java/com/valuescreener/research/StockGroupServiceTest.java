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

    @Mock
    private StockGroupHistoryRepository groupHistory;

    private StockGroupService service;

    @BeforeEach
    void setUp() {
        service = new StockGroupService(groups, snapshots, groupHistory);
    }

    @Test
    void listIsSortedBySortOrderThenName() {
        when(groups.findAll()).thenReturn(List.of(
                new StockGroup("ZED", "Zed", 20),
                new StockGroup("BETA", "Beta", 20),
                new StockGroup("FIRST", "First", 10)));
        when(snapshots.countByGroupKey(any())).thenReturn(3L);

        List<StockGroupView> views = service.list();

        assertThat(views).extracting(StockGroupView::key).containsExactly("FIRST", "BETA", "ZED");
        assertThat(views.get(0).stockCount()).isEqualTo(3L);
    }

    @Test
    void createAppendsAfterLastSortOrder() {
        when(groups.findById("NEW_GROUP")).thenReturn(Optional.empty());
        when(groups.existsByNameIgnoreCase("New Group")).thenReturn(false);
        when(groups.maxSortOrder()).thenReturn(350);
        when(groups.create(any(StockGroup.class))).thenAnswer(i -> i.getArgument(0));

        StockGroupView view = service.create(new CreateStockGroupRequest(" new_group ", "New Group"));

        assertThat(view.key()).isEqualTo("NEW_GROUP");
        assertThat(view.sortOrder()).isEqualTo(360);
        assertThat(view.stockCount()).isZero();
    }

    @Test
    void createRejectsDuplicateKeyOrNameCaseInsensitive() {
        when(groups.findById("BANKS")).thenReturn(Optional.of(new StockGroup("BANKS", "Banks", 10)));
        assertThatThrownBy(() -> service.create(new CreateStockGroupRequest("banks", "Other")))
                .isInstanceOf(StockGroupConflictException.class);

        when(groups.findById("OTHER")).thenReturn(Optional.empty());
        when(groups.existsByNameIgnoreCase("banks")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateStockGroupRequest("OTHER", "banks")))
                .isInstanceOf(StockGroupConflictException.class);
        verify(groups, never()).create(any());
    }

    @Test
    void updateRenamesAndKeepsKey() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(groups.existsByNameIgnoreCase("Banking")).thenReturn(false);
        when(snapshots.countByGroupKey("BANKS")).thenReturn(2L);

        StockGroupView view = service.update("banks", new UpdateStockGroupRequest("Banking", 15));

        assertThat(view.key()).isEqualTo("BANKS");
        assertThat(view.name()).isEqualTo("Banking");
        assertThat(view.sortOrder()).isEqualTo(15);
        assertThat(view.stockCount()).isEqualTo(2L);
        verify(groups).update(banks, "Banks");
    }

    @Test
    void updateWithNullFieldsChangesNothingAndNameClashIsConflict() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByGroupKey("BANKS")).thenReturn(0L);
        assertThat(service.update("BANKS", new UpdateStockGroupRequest(null, null)).name()).isEqualTo("Banks");
        verify(groups, never()).update(any(), any());

        when(groups.existsByNameIgnoreCase("Insurance")).thenReturn(true);
        assertThatThrownBy(() -> service.update("BANKS", new UpdateStockGroupRequest("Insurance", null)))
                .isInstanceOf(StockGroupConflictException.class);
        verify(groups, never()).update(any(), any());
    }

    @Test
    void updateWithBlankNameIsIllegalArgumentWithoutCatalogLookup() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));

        assertThatThrownBy(() -> service.update("BANKS", new UpdateStockGroupRequest("   ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        verify(groups, never()).existsByNameIgnoreCase(any());
        verify(groups, never()).update(any(), any());
    }

    @Test
    void createWithBlankNameIsIllegalArgumentWithoutCatalogLookup() {
        assertThatThrownBy(() -> service.create(new CreateStockGroupRequest("NEW_GROUP", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        verify(groups, never()).existsByNameIgnoreCase(any());
        verify(groups, never()).create(any());
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
        when(snapshots.countByGroupKey("BANKS")).thenReturn(1L);

        assertThatThrownBy(() -> service.delete("BANKS")).isInstanceOf(StockGroupConflictException.class);
        verify(groups, never()).delete(any());
    }

    @Test
    void deleteGroupInHistoryIsConflict() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByGroupKey("BANKS")).thenReturn(0L);
        when(groupHistory.existsByPreviousGroupKeyOrNewGroupKey("BANKS", "BANKS")).thenReturn(true);

        assertThatThrownBy(() -> service.delete("BANKS"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group BANKS appears in the group history and cannot be deleted");
        verify(groups, never()).delete(any());
    }

    @Test
    void deleteChecksSnapshotsThenHistoryThenDeletesInDynamo() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByGroupKey("BANKS")).thenReturn(0L);
        when(groupHistory.existsByPreviousGroupKeyOrNewGroupKey("BANKS", "BANKS")).thenReturn(false);
        org.mockito.Mockito.doThrow(new StockGroupConflictException(
                "Group BANKS appears in the group history and cannot be deleted")).when(groups).delete(banks);

        assertThatThrownBy(() -> service.delete("BANKS"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group BANKS appears in the group history and cannot be deleted");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(snapshots, groupHistory, groups);
        order.verify(snapshots).countByGroupKey("BANKS");
        order.verify(groupHistory).existsByPreviousGroupKeyOrNewGroupKey("BANKS", "BANKS");
        order.verify(groups).delete(banks);
    }

    @Test
    void namesByKeyReadsAllGroupsOnce() {
        when(groups.findAll()).thenReturn(List.of(new StockGroup("BANKS", "Banks", 10),
                new StockGroup("OTHER", "Other", 20)));

        assertThat(service.namesByKey()).containsEntry("BANKS", "Banks").containsEntry("OTHER", "Other");
        verify(groups).findAll();
    }

    @Test
    void deleteUnusedGroupDeletesAndUnknownIsNotFound() {
        StockGroup banks = new StockGroup("BANKS", "Banks", 10);
        when(groups.findById("BANKS")).thenReturn(Optional.of(banks));
        when(snapshots.countByGroupKey("BANKS")).thenReturn(0L);
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
