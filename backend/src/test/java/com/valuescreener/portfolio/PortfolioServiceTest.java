package com.valuescreener.portfolio;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioPositionRepository repository;

    @Test
    void buyCreatesNewPositionWhenIsinUnknown() {
        PortfolioService service = new PortfolioService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTicker()).isEqualTo("AAPL");
        assertThat(captor.getValue().getIsin()).isEqualTo("US0378331005");
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void buyMergesIntoExistingPositionWhenIsinKnown() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("170.00"), LocalDate.of(2026, 3, 1));

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("20");
        assertThat(captor.getValue().getEntryPrice()).isEqualByComparingTo("160.00");
    }

    @Test
    void sellReducesExistingPosition() {
        PortfolioService service = new PortfolioService(repository);
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
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("US0378331005", new BigDecimal("10")));

        verify(repository).delete(existing);
        verify(repository, never()).save(existing);
    }

    @Test
    void sellRejectsUnknownIsin() {
        PortfolioService service = new PortfolioService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sell(new SellPositionRequest("US0378331005", new BigDecimal("1"))))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    void listPublicPositionsExposesTickerAndCompanyName() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition position = new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
        when(repository.findAll()).thenReturn(List.of(position));

        List<PublicPortfolioPositionView> result = service.listPublicPositions();

        assertThat(result).containsExactly(new PublicPortfolioPositionView("MSFT", "Microsoft Corp."));
    }
}
