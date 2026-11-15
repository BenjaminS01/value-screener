package com.valuescreener.portfolio;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

public class PortfolioPosition {

    private String ticker;

    private String isin;

    private String companyName;

    private BigDecimal quantity;

    private BigDecimal entryPrice;

    private LocalDate purchaseDate;

    private Long version;

    private PortfolioPosition() {
        // for restore()
    }

    /** Rebuilds a stored position without re-validating; only the persistence layer uses this. */
    static PortfolioPosition restore(String ticker, String isin, String companyName, BigDecimal quantity,
                                     BigDecimal entryPrice, LocalDate purchaseDate, Long version) {
        PortfolioPosition position = new PortfolioPosition();
        position.ticker = ticker;
        position.isin = isin;
        position.companyName = companyName;
        position.quantity = quantity;
        position.entryPrice = entryPrice;
        position.purchaseDate = purchaseDate;
        position.version = version;
        return position;
    }

    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    public PortfolioPosition(String ticker, String isin, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.quantity = requirePositive(quantity, "quantity");
        this.entryPrice = requirePositive(entryPrice, "entryPrice");
        this.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
    }

    /** Replaces all mutable fields; the ISIN is intentionally not changeable. */
    public void correct(String ticker, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate) {
        String validTicker = requireValidTicker(ticker);
        String validCompanyName = requireNonBlank(companyName, "companyName");
        BigDecimal validQuantity = requirePositive(quantity, "quantity");
        BigDecimal validEntryPrice = requirePositive(entryPrice, "entryPrice");
        LocalDate validPurchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        this.ticker = validTicker;
        this.companyName = validCompanyName;
        this.quantity = validQuantity;
        this.entryPrice = validEntryPrice;
        this.purchaseDate = validPurchaseDate;
    }

    private static String requireValidTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        String normalized = ticker.trim().toUpperCase();
        if (normalized.length() > 10) {
            throw new IllegalArgumentException("ticker must not exceed 10 characters");
        }
        return normalized;
    }

    private static String requireValidIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            throw new IllegalArgumentException("isin must not be blank");
        }
        String normalized = isin.trim().toUpperCase();
        if (!ISIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("isin must be a valid 12-character ISIN");
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    /** Optimistic-lock version; null until the position was saved once. */
    Long version() {
        return version;
    }

    void version(Long version) {
        this.version = version;
    }

    public String getTicker() {
        return ticker;
    }

    public String getIsin() {
        return isin;
    }

    public String getCompanyName() {
        return companyName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void recordPurchase(BigDecimal additionalQuantity, BigDecimal purchasePrice) {
        BigDecimal validQuantity = requirePositive(additionalQuantity, "quantity");
        BigDecimal validPrice = requirePositive(purchasePrice, "purchasePrice");

        BigDecimal existingCost = this.quantity.multiply(this.entryPrice);
        BigDecimal addedCost = validQuantity.multiply(validPrice);
        BigDecimal newQuantity = this.quantity.add(validQuantity);

        this.entryPrice = existingCost.add(addedCost).divide(newQuantity, 6, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    public void recordSale(BigDecimal soldQuantity) {
        BigDecimal validQuantity = requirePositive(soldQuantity, "quantity");
        if (validQuantity.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("sale quantity must not exceed current holding");
        }
        this.quantity = this.quantity.subtract(validQuantity);
    }

    public boolean isClosed() {
        return quantity.signum() == 0;
    }
}
