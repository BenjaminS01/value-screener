package com.valuescreener.research;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Whole-partition reads that follow LastEvaluatedKey (the paginator issues the follow-up requests). */
final class HistoryQuery {

    private HistoryQuery() {
    }

    /** All items of a partition whose sort key starts with the prefix, consistent read. */
    static List<Map<String, AttributeValue>> items(DynamoDbClient client, String table, String pk,
                                                    String skPrefix, boolean newestFirst) {
        QueryRequest request = QueryRequest.builder().tableName(table).consistentRead(true)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :p)")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(pk),
                        ":p", AttributeValue.fromS(skPrefix)))
                .scanIndexForward(!newestFirst).build();
        return all(client, request);
    }

    static List<Map<String, AttributeValue>> all(DynamoDbClient client, QueryRequest request) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        for (QueryResponse page : client.queryPaginator(request)) {
            items.addAll(page.items());
        }
        return items;
    }

    /** Count of matching items over all pages (Select=COUNT; a filter is applied before counting). */
    static long count(DynamoDbClient client, QueryRequest request) {
        long total = 0;
        for (QueryResponse page : client.queryPaginator(request)) {
            total += page.count();
        }
        return total;
    }
}
