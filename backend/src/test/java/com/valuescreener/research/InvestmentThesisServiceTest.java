package com.valuescreener.research;

import com.valuescreener.portfolio.PortfolioPosition;
import com.valuescreener.portfolio.PortfolioPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentThesisServiceTest {

    private static final String ISIN = "US0378331005";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private InvestmentThesisRepository repository;

    @Mock
    private PortfolioPositionRepository positions;

    private InvestmentThesisService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentThesisService(repository, positions, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(repository.saveAndFlush(any(InvestmentThesis.class))).then(returnsFirstArg());
    }

    private static SaveThesisRequest request(Long version) {
        return new SaveThesisRequest("why", "expected", "exit", ThesisStatus.WATCHING, "pricey", version);
    }

    private static InvestmentThesis existing() {
        InvestmentThesis thesis = new InvestmentThesis(ISIN, Instant.parse("2026-10-01T00:00:00Z"));
        thesis.replace("old why", "old exp", "old exit", null, null, Instant.parse("2026-10-01T00:00:00Z"));
        ReflectionTestUtils.setField(thesis, "version", 3L);
        return thesis;
    }

    @Test
    void getReturnsViewWithHoldingFalse() {
        when(repository.findById(ISIN)).thenReturn(Optional.of(existing()));
        when(positions.findByIsin(ISIN)).thenReturn(Optional.empty());

        InvestmentThesisView view = service.get(" us0378331005 ");

        assertThat(view.isin()).isEqualTo(ISIN);
        assertThat(view.whyInteresting()).isEqualTo("old why");
        assertThat(view.holding()).isFalse();
    }

    @Test
    void getUnknownThrowsNotFound() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ISIN)).isInstanceOf(ThesisNotFoundException.class);
    }

    @Test
    void holdingIsTrueWhenInPortfolioAndStoredStatusStillReturned() {
        InvestmentThesis thesis = existing();
        thesis.replace("w", null, null, ThesisStatus.REJECTED, "bad", NOW);
        when(repository.findById(ISIN)).thenReturn(Optional.of(thesis));
        when(positions.findByIsin(ISIN)).thenReturn(Optional.of(new PortfolioPosition(
                "AAPL", ISIN, "Apple", new BigDecimal("1"), new BigDecimal("1"), LocalDate.of(2026, 1, 1))));

        InvestmentThesisView view = service.get(ISIN);

        assertThat(view.holding()).isTrue();
        assertThat(view.status()).isEqualTo(ThesisStatus.REJECTED);
        assertThat(view.statusReason()).isEqualTo("bad");
    }

    @Test
    void saveWithoutVersionCreatesThesis() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        InvestmentThesisView view = service.save("us0378331005", request(null));

        ArgumentCaptor<InvestmentThesis> captor = ArgumentCaptor.forClass(InvestmentThesis.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getIsin()).isEqualTo(ISIN);
        assertThat(view.whyInteresting()).isEqualTo("why");
        assertThat(view.status()).isEqualTo(ThesisStatus.WATCHING);
        assertThat(view.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void saveWithoutVersionWhenThesisExistsIsConflict() {
        when(repository.findById(ISIN)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.save(ISIN, request(null)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void saveWithMatchingVersionReplacesFields() {
        InvestmentThesis thesis = existing();
        when(repository.findById(ISIN)).thenReturn(Optional.of(thesis));

        InvestmentThesisView view = service.save(ISIN, request(3L));

        assertThat(view.whyInteresting()).isEqualTo("why");
        assertThat(thesis.getExitCriteria()).isEqualTo("exit");
        assertThat(thesis.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void saveWithStaleVersionIsConflict() {
        InvestmentThesis thesis = existing();
        when(repository.findById(ISIN)).thenReturn(Optional.of(thesis));

        assertThatThrownBy(() -> service.save(ISIN, request(8L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(thesis.getWhyInteresting()).isEqualTo("old why");
    }

    @Test
    void saveWithVersionForMissingThesisIsConflict() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(ISIN, request(3L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void saveStoresBlankTextsAsNull() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        InvestmentThesisView view = service.save(ISIN,
                new SaveThesisRequest("  ", "", null, null, null, null));

        assertThat(view.whyInteresting()).isNull();
        assertThat(view.expectedDevelopment()).isNull();
        assertThat(view.exitCriteria()).isNull();
    }

    @Test
    void saveStatusReasonWithoutStatusIsIllegalArgument() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(ISIN, new SaveThesisRequest("a", "b", "c", null, "why", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("statusReason requires status");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void saveRejectsMalformedIsin() {
        assertThatThrownBy(() -> service.save("NOPE", request(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void appendCreatesThesisWhenMissingUsingClockDate() {
        when(repository.findById(ISIN)).thenReturn(Optional.empty());

        service.appendPurchaseReason(ISIN, "  Moat intact ");

        ArgumentCaptor<InvestmentThesis> captor = ArgumentCaptor.forClass(InvestmentThesis.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWhyInteresting()).isEqualTo("[2026-11-12] Kauf: Moat intact");
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void appendToExistingKeepsText() {
        InvestmentThesis thesis = existing();
        when(repository.findById(ISIN)).thenReturn(Optional.of(thesis));

        service.appendPurchaseReason(ISIN, "Moat intact");

        assertThat(thesis.getWhyInteresting()).isEqualTo("old why\n\n[2026-11-12] Kauf: Moat intact");
        verify(repository).save(thesis);
    }

    @Test
    void appendBeyondLimitThrowsAndDoesNotSave() {
        InvestmentThesis thesis = existing();
        thesis.replace("x".repeat(3995), null, null, null, null, NOW);
        when(repository.findById(ISIN)).thenReturn(Optional.of(thesis));

        assertThatThrownBy(() -> service.appendPurchaseReason(ISIN, "Moat intact"))
                .isInstanceOf(ThesisTooLongException.class).hasMessage("Thesis text too long");
        verify(repository, never()).save(any());
    }
}
