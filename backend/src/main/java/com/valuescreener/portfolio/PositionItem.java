package com.valuescreener.portfolio;

import com.valuescreener.persistence.ItemConditions;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Storage shape of a {@link PortfolioPosition}: pk "POSITION", sk "ISIN#" + isin. */
@DynamoDbBean
public class PositionItem {

    public static final TableSchema<PositionItem> SCHEMA = TableSchema.fromBean(PositionItem.class);
    static final String PK = "POSITION";

    private String pk;
    private String sk;
    private String ticker;
    private String isin;
    private String companyName;
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private LocalDate purchaseDate;
    private Long version;

    public static String sortKey(String isin) {
        return "ISIN#" + isin;
    }

    public static PositionItem from(PortfolioPosition p) {
        PositionItem item = new PositionItem();
        item.pk = PK;
        item.sk = sortKey(p.getIsin());
        item.ticker = p.getTicker();
        item.isin = p.getIsin();
        item.companyName = p.getCompanyName();
        item.quantity = p.getQuantity();
        item.entryPrice = p.getEntryPrice();
        item.purchaseDate = p.getPurchaseDate();
        item.version = p.version();
        return item;
    }

    public PortfolioPosition toDomain() {
        return PortfolioPosition.restore(ticker, isin, companyName, quantity, entryPrice, purchaseDate, version);
    }

    /** Call after the item was written: the stored version is one higher than the one the object carried. */
    public static void markSaved(PortfolioPosition p) {
        p.version(ItemConditions.nextVersion(p.version()));
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
