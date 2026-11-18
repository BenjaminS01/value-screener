package com.valuescreener.portfolio;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.ItemConditions;
import com.valuescreener.thesis.InvestmentThesis;
import com.valuescreener.thesis.ThesisItem;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Component
class DynamoPurchaseWriter implements PurchaseWriter {

    private final DynamoDbEnhancedClient client;
    private final DynamoDbTable<PositionItem> positions;
    private final DynamoDbTable<ThesisItem> theses;

    DynamoPurchaseWriter(DynamoDbEnhancedClient client, DynamoProperties properties) {
        this.client = client;
        this.positions = client.table(properties.tableName(), PositionItem.SCHEMA);
        this.theses = client.table(properties.tableName(), ThesisItem.SCHEMA);
    }

    @Override
    public void save(PortfolioPosition position, InvestmentThesis thesis) {
        try {
            client.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addPutItem(theses, TransactPutItemEnhancedRequest.builder(ThesisItem.class)
                            .item(ThesisItem.from(thesis))
                            .conditionExpression(ItemConditions.createOnlyIfNew(thesis.getVersion()))
                            .build())
                    .addPutItem(positions, TransactPutItemEnhancedRequest.builder(PositionItem.class)
                            .item(PositionItem.from(position))
                            .conditionExpression(ItemConditions.createOnlyIfNew(position.version()))
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            if (ItemConditions.isConflict(e)) {
                throw new ConcurrentUpdateException(
                        "Purchase of " + position.getIsin() + " lost against a concurrent change", e);
            }
            throw e;
        }
        ThesisItem.markSaved(thesis);
        PositionItem.markSaved(position);
    }
}
