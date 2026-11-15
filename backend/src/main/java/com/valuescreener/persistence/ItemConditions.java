package com.valuescreener.persistence;

import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;


/** Shared condition handling for versioned items (optimistic locking). */
public final class ItemConditions {

    private ItemConditions() {
    }

    /**
     * Extra condition for a put: a new object (no version yet) may only create an item that does not
     * exist. Existing objects get their version condition from the VersionedRecordExtension.
     */
    public static Expression createOnlyIfNew(Long version) {
        return version != null ? null
                : Expression.builder().expression("attribute_not_exists(pk)").build();
    }

    /** Condition for deleting exactly the version the caller loaded. */
    public static Expression versionEquals(Long version) {
        if (version == null) {
            throw new IllegalArgumentException("cannot delete an item that was never saved");
        }
        return Expression.builder().expression("#v = :v").putExpressionName("#v", "version")
                .putExpressionValue(":v", AttributeValue.fromN(version.toString()))
                .build();
    }

    /** Next stored version: the extension starts at 1 and adds 1 per write. */
    public static long nextVersion(Long current) {
        return current == null ? 1L : current + 1;
    }

    /** True when a cancelled transaction lost against another writer (condition failed or item locked). */
    public static boolean isConflict(TransactionCanceledException e) {
        return e.cancellationReasons().stream().map(CancellationReason::code)
                .anyMatch(c -> "ConditionalCheckFailed".equals(c) || "TransactionConflict".equals(c));
    }
}
