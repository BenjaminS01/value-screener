package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.valuescreener.research.InvestmentThesisService;
import com.valuescreener.research.ThesisTooLongException;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioPositionRepository repository;

    @Mock
    private com.valuescreener.research.StockGroupLookup lookup;

    @Mock
    private InvestmentThesisService theses;

    @Test
    void buyCreatesNewPositionWhenIsinUnknown() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), null);

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTicker()).isEqualTo("AAPL");
        assertThat(captor.getValue().getIsin()).isEqualTo("US0378331005");
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void buyMergesIntoExistingPositionWhenIsinKnown() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("170.00"), LocalDate.of(2026, 3, 1), null);

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("20");
        assertThat(captor.getValue().getEntryPrice()).isEqualByComparingTo("160.00");
    }

    @Test
    void sellReducesExistingPosition() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("US0378331005", new BigDecimal("4")));

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("6");
    }

    @Test
    void sellDeletesPositionWhenFullyClosed() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("US0378331005", new BigDecimal("10")));

        verify(repository).delete(existing);
        verify(repository, never()).save(existing);
    }

    @Test
    void sellRejectsUnknownIsin() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sell(new SellPositionRequest("US0378331005", new BigDecimal("1"))))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    void listForUserExposesOnlyTickerIsinAndCompanyName() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition position = new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
        when(repository.findAll()).thenReturn(List.of(position));

        assertThat(service.listForUser())
                .containsExactly(new PortfolioPositionView("MSFT", "US5949181045", "Microsoft Corp.", null));
    }

    @Test
    void listForAdminIncludesHoldings() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition position = new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
        when(repository.findAll()).thenReturn(List.of(position));

        assertThat(service.listForAdmin()).containsExactly(new AdminPortfolioPositionView(
                "MSFT", "US5949181045", "Microsoft Corp.", null, new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1)));
    }

    @Test
    void positionsCarryGroupFromSnapshotOrNull() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition msft = new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
        PortfolioPosition aapl = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("1"), new BigDecimal("100.00"), LocalDate.of(2026, 2, 1));
        when(repository.findAll()).thenReturn(List.of(msft, aapl));
        com.valuescreener.research.StockGroupRef software = new com.valuescreener.research.StockGroupRef("SOFTWARE", "Software");
        when(lookup.groupsByIsin(List.of("US5949181045", "US0378331005")))
                .thenReturn(java.util.Map.of("US5949181045", software));

        assertThat(service.listForUser()).containsExactly(
                new PortfolioPositionView("MSFT", "US5949181045", "Microsoft Corp.", software),
                new PortfolioPositionView("AAPL", "US0378331005", "Apple Inc.", null));
        assertThat(service.listForAdmin()).extracting(AdminPortfolioPositionView::group)
                .containsExactly(software, null);
    }

    private static CorrectPositionRequest correction() {
        return new CorrectPositionRequest("AAPL", "Apple Inc.", new BigDecimal("3"), new BigDecimal("99.00"), LocalDate.of(2026, 4, 1));
    }

    @Test
    void correctUnknownIsinThrowsNotFound() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correct("US0378331005", correction()))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    void correctUpdatesPositionAndSaves() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.correct("US0378331005", correction());

        verify(repository).save(existing);
        assertThat(existing.getQuantity()).isEqualByComparingTo("3");
        assertThat(existing.getEntryPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void deleteRemovesPosition() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.delete("US0378331005");

        verify(repository).delete(existing);
    }

    @Test
    void deleteUnknownIsinThrowsNotFound() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("US0378331005"))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    void correctAndDeleteNormalizeIsin() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.correct(" us0378331005 ", correction());
        service.delete(" us0378331005 ");

        verify(repository).save(existing);
        verify(repository).delete(existing);
    }

    @Test
    void buyNormalizesIsinForLookupAndMergesIntoExisting() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.buy(new BuyPositionRequest("aapl", " us0378331005 ", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("170.00"), LocalDate.of(2026, 3, 1), null));

        assertThat(existing.getQuantity()).isEqualByComparingTo("20");
        verify(repository).save(existing);
    }

    @Test
    void buyStoresNormalizedIsinOnNewPosition() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        service.buy(new BuyPositionRequest("aapl", " us0378331005 ", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), null));

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIsin()).isEqualTo("US0378331005");
    }

    @Test
    void sellNormalizesIsinForLookup() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("us0378331005 ", new BigDecimal("4")));

        assertThat(existing.getQuantity()).isEqualByComparingTo("6");
    }

    @Test
    void buyWithPurchaseReasonAppendsToThesisWithNormalizedIsinBeforeSaving() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        service.buy(new BuyPositionRequest("aapl", " us0378331005 ", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), "Moat intact"));

        InOrder order = inOrder(theses, repository);
        order.verify(theses).appendPurchaseReason("US0378331005", "Moat intact");
        order.verify(repository).save(any(PortfolioPosition.class));
    }

    @Test
    void buyWithoutOrBlankPurchaseReasonDoesNotTouchThesis() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        service.buy(new BuyPositionRequest("aapl", "US0378331005", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), "   "));
        service.buy(new BuyPositionRequest("aapl", "US0378331005", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), null));

        verifyNoInteractions(theses);
    }

    @Test
    void buyFailsWithoutSavingWhenThesisAppendIsTooLong() {
        PortfolioService service = new PortfolioService(repository, lookup, theses);
        doThrow(new ThesisTooLongException()).when(theses).appendPurchaseReason("US0378331005", "x");

        assertThatThrownBy(() -> service.buy(new BuyPositionRequest("aapl", "US0378331005", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15), "x")))
                .isInstanceOf(ThesisTooLongException.class);

        verify(repository, never()).save(any());
    }
}
