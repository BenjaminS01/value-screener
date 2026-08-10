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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySnapshotServiceTest {

    @Mock
    private CompanySnapshotRepository repository;

    private static FindingRequest peRatioFinding(String value) {
        return new FindingRequest(ResearchCriterion.PE_RATIO, new BigDecimal(value), null,
                "Trailing P/E of " + value + ".", "https://example.com/aapl-key-stats",
                LocalDate.of(2026, 8, 1));
    }

    @Test
    void upsertCreatesNewSnapshotWhenIsinUnknown() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", List.of(peRatioFinding("28.0")));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).criterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
        assertThat(result.findings().get(0).numericValue()).isEqualByComparingTo("28.0");
    }

    @Test
    void upsertOnExistingSnapshotPreservesFindingsNotIncludedInThisRequest() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot existing = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/first-source", LocalDate.of(2026, 8, 1));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.",
                List.of(new FindingRequest(ResearchCriterion.MOAT_ASSESSMENT, null, null,
                        "Wide moat from ecosystem lock-in.", "https://example.com/moat-analysis",
                        LocalDate.of(2026, 8, 5))));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.businessDescription())
                .isEqualTo("Designs and sells consumer electronics and services.");
        assertThat(result.findings()).hasSize(2);
        assertThat(result.findings())
                .extracting(FindingView::criterionKey)
                .containsExactlyInAnyOrder(ResearchCriterion.PE_RATIO, ResearchCriterion.MOAT_ASSESSMENT);

        ArgumentCaptor<CompanySnapshot> captor = ArgumentCaptor.forClass(CompanySnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void upsertOfSameCriterionAgainReplacesRatherThanDuplicates() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot existing = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/first-source", LocalDate.of(2026, 8, 1));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", List.of(peRatioFinding("29.5")));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).numericValue()).isEqualByComparingTo("29.5");
    }

    @Test
    void listAllReturnsAllSnapshots() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
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
                "Designs and sells consumer electronics.");
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
