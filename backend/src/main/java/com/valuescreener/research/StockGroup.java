package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.regex.Pattern;

@Entity
@Table(name = "stock_group")
public class StockGroup {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");
    /** Reserved: "?group=none" filters ungrouped stocks. */
    private static final String RESERVED_NONE = "NONE";

    @Id
    @Column(name = "key", length = 40)
    private String key;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(nullable = false)
    private Long version;

    protected StockGroup() {
        // JPA
    }

    public StockGroup(String key, String name, int sortOrder) {
        this.key = normalizeKey(key);
        this.name = requireValidName(name);
        this.sortOrder = sortOrder;
    }

    /** Trim + uppercase; shared by the constructor and lookups. Does not validate the pattern. */
    static String trimUpper(String key) {
        return key == null ? "" : key.trim().toUpperCase();
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

    public String getKey() { return key; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
}
