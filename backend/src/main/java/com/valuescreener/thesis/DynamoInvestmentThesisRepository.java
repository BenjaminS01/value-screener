package com.valuescreener.thesis;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.ItemConditions;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;

import java.util.Optional;

@Repository
public class DynamoInvestmentThesisRepository implements InvestmentThesisRepository {

    private final DynamoDbTable<ThesisItem> table;

    public DynamoInvestmentThesisRepository(DynamoDbEnhancedClient client, DynamoProperties properties) {
        this.table = client.table(properties.tableName(), ThesisItem.SCHEMA);
    }

    @Override
    public Optional<InvestmentThesis> findById(String isin) {
        ThesisItem item = table.getItem(GetItemEnhancedRequest.builder()
                .key(Key.builder().partitionValue(ThesisItem.PK).sortValue(ThesisItem.sortKey(isin)).build())
                .consistentRead(true).build());
        return Optional.ofNullable(item).map(ThesisItem::toDomain);
    }

    @Override
    public InvestmentThesis save(InvestmentThesis thesis) {
        ItemConditions.guarded("Thesis " + thesis.getIsin() + " was changed concurrently",
                () -> table.putItem(PutItemEnhancedRequest.builder(ThesisItem.class)
                        .item(ThesisItem.from(thesis))
                        .conditionExpression(ItemConditions.createOnlyIfNew(thesis.getVersion()))
                        .build()));
        ThesisItem.markSaved(thesis);
        return thesis;
    }
}
