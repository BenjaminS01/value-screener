package com.valuescreener.research.group;

import java.util.regex.Pattern;

/** A stock group of the owner's catalog; stored in DynamoDB (see {@link StockGroupItem}). */
public class StockGroup {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");
    /** Reserved: "?group=none" filters ungrouped stocks. */
    private static final String RESERVED_NONE = "NONE";

    private String key;
    private String name;
    private int sortOrder;
    /** Optimistic-lock version; null until the group was saved once. */
    private Long version;

    private StockGroup() {
        // for restore()
    }

    /** Rebuilds a stored group without re-validating it. */
    static StockGroup restore(String key, String name, int sortOrder, Long version) {
        StockGroup group = new StockGroup();
        group.key = key;
        group.name = name;
        group.sortOrder = sortOrder;
        group.version = version;
        return group;
    }

    public StockGroup(String key, String name, int sortOrder) {
        this.key = normalizeKey(key);
        this.name = requireValidName(name);
        this.sortOrder = sortOrder;
    }

    /** Trim + uppercase; shared by the constructor and lookups. Does not validate the pattern. */
    public static String trimUpper(String key) {
        return key == null ? "" : key.trim().toUpperCase();
    }

    /** True for a normalized key that can exist (pattern and length; NONE is reserved). */
    static boolean isValidKey(String normalized) {
        return normalized != null && KEY_PATTERN.matcher(normalized).matches() && !RESERVED_NONE.equals(normalized);
    }

    private static String normalizeKey(String key) {
        String normalized = trimUpper(key);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("key must match [A-Z][A-Z0-9_]{1,39}");
        }
        if (RESERVED_NONE.equals(normalized)) {
            throw new IllegalArgumentException("key NONE is reserved");
        }
        return normalized;
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("name must not exceed 100 characters");
        }
        return trimmed;
    }

    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    public void moveTo(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    Long version() {
        return version;
    }

    void version(Long version) {
        this.version = version;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
}
