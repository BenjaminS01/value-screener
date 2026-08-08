package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySnapshotServiceTest {

    @Mock
    private CompanySnapshotRepository repository;

    @Test
    void upsertCreatesNewSnapshotWhenIsinUnknown() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                "Regulatory risk in the EU; expansion opportunity in services.",
                new BigDecimal("28.0"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.peRatio()).isEqualByComparingTo("28.0");
        assertThat(result.updatedFields()).containsExactlyInAnyOrder(
                "peRatio", "moatNote", "opportunitiesAndRisksNote");
    }

    @Test
    void upsertMergesIntoExistingSnapshotWhenIsinKnown() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        FinancialStats existingStats = new FinancialStats(new BigDecimal("28.0"), new BigDecimal("40.0"), null, null, null, null, null, null);
        CompanySnapshot existing = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                "Regulatory risk in the EU.", existingStats,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/first-source"));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.", null, null,
                new BigDecimal("29.5"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 5), Set.of("https://example.com/second-source"));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.peRatio()).isEqualByComparingTo("29.5");
        assertThat(result.pbRatio()).isEqualByComparingTo("40.0");
        assertThat(result.opportunitiesAndRisksNote()).isEqualTo("Regulatory risk in the EU.");
        assertThat(result.sources()).containsExactlyInAnyOrder(
                "https://example.com/first-source", "https://example.com/second-source");

        ArgumentCaptor<CompanySnapshot> captor = ArgumentCaptor.forClass(CompanySnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void listAllReturnsAllSnapshots() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, null,
                LocalDate.of(2026, 8, 1), Set.of());
        when(repository.findAll()).thenReturn(List.of(snapshot));

        List<CompanySnapshotView> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("AAPL");
    }

    @Test
    void getByIsinReturnsSnapshotWhenFound() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, null,
                LocalDate.of(2026, 8, 1), Set.of());
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(snapshot));

        assertThat(service.getByIsin("US0378331005").isin()).isEqualTo("US0378331005");
    }

    @Test
    void getByIsinThrowsWhenNotFound() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIsin("US0378331005"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }
}
