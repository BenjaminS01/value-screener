package com.valuescreener.portfolio;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import com.valuescreener.thesis.DynamoInvestmentThesisRepository;
import com.valuescreener.thesis.InvestmentThesis;
import com.valuescreener.thesis.InvestmentThesisRepository;
import com.valuescreener.thesis.InvestmentThesisService;
import com.valuescreener.thesis.ThesisTooLongException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Buy with purchase reason against real DynamoDB Local: position and thesis are written atomically. */
class PortfolioBuyTransactionTest extends DynamoTestSupport {

    private static final String ISIN = "US0378331005";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private final DynamoDbClient client = newClient();
    private DynamoPurchaseWriter writer;
    private PortfolioPositionRepository positions;
    private InvestmentThesisRepository theses;
    private InvestmentThesisService thesisService;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        DynamoProperties props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        writer = new DynamoPurchaseWriter(enhanced, props);
        positions = new DynamoPortfolioPositionRepository(enhanced, props);
        theses = new DynamoInvestmentThesisRepository(enhanced, props);
        thesisService = new InvestmentThesisService(theses, positions, Clock.fixed(NOW, ZoneOffset.UTC));
        service = new PortfolioService(positions, Mockito.mock(com.valuescreener.research.group.StockGroupLookup.class),
                thesisService, writer);
    }

    private static BuyPositionRequest buy(String quantity, String reason) {
        return new BuyPositionRequest("aapl", ISIN, "Apple Inc.", new BigDecimal(quantity),
                new BigDecimal("150.00"), LocalDate.of(2026, 11, 12), reason);
    }

    @Test
    void buyWithReasonCreatesPositionAndThesisTogether() {
        service.buy(buy("10", "Moat intact"));

        PortfolioPosition position = positions.findByIsin(ISIN).orElseThrow();
        InvestmentThesis thesis = theses.findById(ISIN).orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("10");
        assertThat(thesis.getWhyInteresting()).isEqualTo("[2026-11-12] Buy: Moat intact");
        // proves the version attribute is set by the VersionedRecordExtension inside transactWriteItems
        assertThat(position.version()).isEqualTo(1L);
        assertThat(thesis.getVersion()).isEqualTo(1L);
    }

    @Test
    void secondBuyWithReasonUpdatesBothUnderTheirVersionConditions() {
        service.buy(buy("10", "first"));
        service.buy(buy("5", "second"));

        PortfolioPosition position = positions.findByIsin(ISIN).orElseThrow();
        InvestmentThesis thesis = theses.findById(ISIN).orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("15");
        assertThat(position.version()).isEqualTo(2L);
        assertThat(thesis.getVersion()).isEqualTo(2L);
        assertThat(thesis.getWhyInteresting()).contains("first").contains("second");
    }

    @Test
    void versionsOfTheObjectsAreCorrectAfterTheTransaction() {
        InvestmentThesis thesis = thesisService.prepareAppendPurchaseReason(ISIN, "why");
        PortfolioPosition position = new PortfolioPosition("AAPL", ISIN, "Apple", new BigDecimal("1"),
                new BigDecimal("1"), LocalDate.of(2026, 1, 1));
        writer.save(position, thesis);

        assertThat(position.version()).isEqualTo(1L);
        assertThat(thesis.getVersion()).isEqualTo(1L);
        // the in-memory objects can be saved again without a conflict
        position.recordPurchase(new BigDecimal("1"), new BigDecimal("1"));
        positions.save(position);
        assertThat(positions.findByIsin(ISIN).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void concurrentlyChangedPositionRollsBackTheThesisWrite() {
        service.buy(buy("10", null));
        // a purchase prepared from the state that is current now ...
        InvestmentThesis thesis = thesisService.prepareAppendPurchaseReason(ISIN, "late reason");
        PortfolioPosition stale = positions.findByIsin(ISIN).orElseThrow();
        // ... loses against someone changing the position in between
        PortfolioPosition winner = positions.findByIsin(ISIN).orElseThrow();
        winner.recordPurchase(new BigDecimal("1"), new BigDecimal("1"));
        positions.save(winner);
        stale.recordPurchase(new BigDecimal("2"), new BigDecimal("1"));

        assertThatThrownBy(() -> writer.save(stale, thesis)).isInstanceOf(ConcurrentUpdateException.class);

        assertThat(theses.findById(ISIN)).isEmpty();
        assertThat(positions.findByIsin(ISIN).orElseThrow().getQuantity()).isEqualByComparingTo("11");
        assertThat(thesis.getVersion()).isNull();
        assertThat(stale.version()).isEqualTo(1L);
    }

    @Test
    void concurrentlyCreatedPositionConflictsAndLeavesThesisUnchanged() {
        // thesis prepared (and position new) while nobody owns the ISIN yet ...
        InvestmentThesis thesis = thesisService.prepareAppendPurchaseReason(ISIN, "why");
        PortfolioPosition mine = new PortfolioPosition("AAPL", ISIN, "Apple", new BigDecimal("1"),
                new BigDecimal("1"), LocalDate.of(2026, 1, 1));
        // ... then someone else creates the position
        positions.save(new PortfolioPosition("AAPL", ISIN, "Apple", new BigDecimal("7"),
                new BigDecimal("1"), LocalDate.of(2026, 1, 1)));

        assertThatThrownBy(() -> writer.save(mine, thesis)).isInstanceOf(ConcurrentUpdateException.class);

        assertThat(theses.findById(ISIN)).isEmpty();
        assertThat(positions.findByIsin(ISIN).orElseThrow().getQuantity()).isEqualByComparingTo("7");
    }

    @Test
    void concurrentlyChangedThesisRollsBackThePositionWrite() {
        service.buy(buy("10", "first"));
        InvestmentThesis stale = thesisService.prepareAppendPurchaseReason(ISIN, "second");
        PortfolioPosition position = positions.findByIsin(ISIN).orElseThrow();
        position.recordPurchase(new BigDecimal("5"), new BigDecimal("1"));
        // someone edits the thesis in between
        InvestmentThesis winner = theses.findById(ISIN).orElseThrow();
        winner.replace("edited", null, null, null, null, NOW);
        theses.save(winner);

        assertThatThrownBy(() -> writer.save(position, stale)).isInstanceOf(ConcurrentUpdateException.class);

        assertThat(theses.findById(ISIN).orElseThrow().getWhyInteresting()).isEqualTo("edited");
        assertThat(positions.findByIsin(ISIN).orElseThrow().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void buyWithInvalidInputAndReasonLeavesThesisUnchanged() {
        assertThatThrownBy(() -> service.buy(buy("-1", "why"))).isInstanceOf(IllegalArgumentException.class);

        assertThat(theses.findById(ISIN)).isEmpty();
        assertThat(positions.findByIsin(ISIN)).isEmpty();
    }

    @Test
    void buyWithInvalidInputOnExistingPositionLeavesThesisUnchanged() {
        service.buy(buy("10", "first"));
        String before = theses.findById(ISIN).orElseThrow().getWhyInteresting();

        assertThatThrownBy(() -> service.buy(buy("0", "second"))).isInstanceOf(IllegalArgumentException.class);

        assertThat(theses.findById(ISIN).orElseThrow().getWhyInteresting()).isEqualTo(before);
    }

    @Test
    void thesisTooLongWritesNoPosition() {
        InvestmentThesis existing = new InvestmentThesis(ISIN, NOW);
        existing.replace("x".repeat(3995), null, null, null, null, NOW);
        theses.save(existing);

        assertThatThrownBy(() -> service.buy(buy("10", "Moat intact"))).isInstanceOf(ThesisTooLongException.class);

        assertThat(positions.findByIsin(ISIN)).isEmpty();
    }

    @Test
    void buyWithoutReasonOnlyWritesThePosition() {
        service.buy(buy("10", null));

        assertThat(positions.findByIsin(ISIN)).isPresent();
        assertThat(theses.findById(ISIN)).isEmpty();
    }
}
