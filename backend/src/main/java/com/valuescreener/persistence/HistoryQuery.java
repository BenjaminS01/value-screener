package com.valuescreener.persistence;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Whole-partition reads that follow LastEvaluatedKey (the paginator issues the follow-up requests). */
public final class HistoryQuery {

    private HistoryQuery() {
    }

    /** All items of a partition whose sort key starts with the prefix (eventually consistent: read-only views). */
    public static List<Map<String, AttributeValue>> items(DynamoDbClient client, String table, String pk,
                                                    String skPrefix, boolean newestFirst) {
        QueryRequest request = QueryRequest.builder().tableName(table).consistentRead(false)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :p)")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(pk),
                        ":p", AttributeValue.fromS(skPrefix)))
                .scanIndexForward(!newestFirst).build();
        return all(client, request);
    }

    public static List<Map<String, AttributeValue>> all(DynamoDbClient client, QueryRequest request) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        for (QueryResponse page : client.queryPaginator(request)) {
            items.addAll(page.items());
        }
        return items;
    }
}
