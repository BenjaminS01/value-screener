package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** A researched company; stored in DynamoDB (see {@link SnapshotItem}), findings embedded in the item. */
public class CompanySnapshot {

    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private String ticker;
    private String isin;
    private String companyName;
    /** Key of the assigned group (stock groups are separate items); null together with groupSource. */
    private String groupKey;
    private GroupSource groupSource;
    private String country;
    private String businessDescription;
    private final List<ResearchFinding> findings = new ArrayList<>();
    /** Optimistic-lock version; null until the snapshot was saved once. */
    private Long version;

    private CompanySnapshot() {
        // for restore()
    }

    /**
     * Rebuilds a stored snapshot without re-validating the identity fields. The group pair rule (formerly a
     * CHECK constraint) is still enforced: key and source are both set or both null.
     */
    static CompanySnapshot restore(String ticker, String isin, String companyName, String country,
                                   String businessDescription, String groupKey, GroupSource groupSource,
                                   List<ResearchFinding> findings, Long version) {
        requireGroupPair(groupKey, groupSource);
        CompanySnapshot snapshot = new CompanySnapshot();
        snapshot.ticker = ticker;
        snapshot.isin = isin;
        snapshot.companyName = companyName;
        snapshot.country = country;
        snapshot.businessDescription = businessDescription;
        snapshot.groupKey = groupKey;
        snapshot.groupSource = groupSource;
        snapshot.findings.addAll(findings);
        snapshot.version = version;
        return snapshot;
    }

    private static void requireGroupPair(String groupKey, GroupSource groupSource) {
        if ((groupKey == null) != (groupSource == null)) {
            throw new IllegalArgumentException("groupKey and groupSource must both be set or both be empty");
        }
    }

    public CompanySnapshot(String ticker, String isin, String companyName, String country,
                            String businessDescription) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
    }

    public void applyUpdate(String companyName, String country, String businessDescription) {
        this.companyName = requireNonBlank(companyName, "companyName");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
    }

    /**
     * Sets the group unless it is MANUAL and the proposal comes from AI: the owner's choice always wins.
     * @return true if the group was set, false if the proposal was ignored
     */
    public boolean assignGroup(StockGroup group, GroupSource source) {
        if (group == null || source == null) {
            throw new IllegalArgumentException("group and source must not be null");
        }
        if (this.groupSource == GroupSource.MANUAL && source == GroupSource.AI) {
            return false;
        }
        this.groupKey = group.getKey();
        this.groupSource = source;
        return true;
    }

    /**
     * Adds or updates the finding for a criterion.
     * @return the previous content if an existing finding was changed; empty for a new criterion or identical content
     */
    public Optional<ReplacedFinding> upsertFinding(ResearchCriterion criterionKey, BigDecimal numericValue,
                                                   Boolean booleanValue, String claim, String sourceUrl,
                                                   LocalDate asOfDate) {
        for (ResearchFinding finding : findings) {
            if (finding.getCriterionKey() == criterionKey) {
                boolean changed = finding.differsFrom(numericValue, booleanValue, claim, sourceUrl, asOfDate);
                ReplacedFinding previous = changed ? new ReplacedFinding(criterionKey, finding.getNumericValue(),
                        finding.getBooleanValue(), finding.getClaim(), finding.getSourceUrl(),
                        finding.getAsOfDate()) : null;
                finding.applyUpdate(numericValue, booleanValue, claim, sourceUrl, asOfDate);
                return Optional.ofNullable(previous);
            }
        }
        ResearchFinding finding = new ResearchFinding(criterionKey, numericValue, booleanValue, claim,
                sourceUrl, asOfDate);
        findings.add(finding);
        return Optional.empty();
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

    /** True for a normalized (trimmed, upper case) ISIN; anything else can never be stored, so never look it up. */
    public static boolean isValidIsin(String normalized) {
        return normalized != null && ISIN_PATTERN.matcher(normalized).matches();
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

    Long version() { return version; }
    void version(Long version) { this.version = version; }
    public String getTicker() { return ticker; }
    public String getIsin() { return isin; }
    public String getCompanyName() { return companyName; }
    public String getGroupKey() { return groupKey; }
    public GroupSource getGroupSource() { return groupSource; }
    public String getCountry() { return country; }
    public String getBusinessDescription() { return businessDescription; }
    public List<ResearchFinding> getFindings() { return List.copyOf(findings); }
}
