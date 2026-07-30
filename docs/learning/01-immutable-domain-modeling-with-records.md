# Immutable Domain Modeling with Java Records

**Where this comes from:** Task 2 of the Company Research Agent build — the `ConfidenceLevel`,
`SourceReference`, and `CompanyResearchResult` types.

**Level:** junior → mid-level. Assumes you know what a Java class is; does not assume you've used
records before.

## The problem records solve

Before records (Java 16+), a simple immutable data holder needed a lot of ceremony:

```java
public final class SourceReference {
    private final String url;
    private final String claim;

    public SourceReference(String url, String claim) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
        this.url = url;
        this.claim = claim;
    }

    public String url() { return url; }
    public String claim() { return claim; }

    @Override public boolean equals(Object o) { /* ... */ }
    @Override public int hashCode() { /* ... */ }
    @Override public String toString() { /* ... */ }
}
```

That's a lot of boilerplate for "two strings, validated, immutable, comparable by value." A `record`
generates the constructor, accessors, `equals`, `hashCode`, and `toString` for you:

```java
public record SourceReference(String url, String claim) {
    public SourceReference {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
    }
}
```

## The compact constructor

The block after `public SourceReference {` (no parameter list!) is a **compact constructor**. It
runs *before* the fields are assigned, and you write validation/normalization logic without
repeating the parameter list or writing `this.url = url` yourself — the compiler still does the
field assignment after your block runs, using the (possibly still-original) parameter values.

This matters for a design reason, not just a syntax one: a compact constructor is the place to
enforce **invariants** — conditions that must always hold for every instance that ever exists. Here,
"a source reference always has a real URL and a real claim" is an invariant of the domain concept
itself, not an implementation detail. Putting it in the compact constructor means it's *physically
impossible* to construct a `SourceReference` that violates it, anywhere in the codebase, forever —
compare that to a regular class where a developer six months from now could add a new constructor
overload and forget the validation.

## Why records fit a domain like this one particularly well

`CompanyResearchResult` represents the outcome of one research call: ticker, company name, a
confidence level, a list of source references, a moat assessment, etc. Once that result exists, it
should never change — it's a fact about what the AI said at a point in time, not a mutable
work-in-progress object. Records make that guarantee at the type level: there are no setters, full
stop. If you need a "changed" version, you construct a new record (records support this cleanly via
`with`-style copying patterns in newer Java, though this project didn't need that yet).

Compare this to a domain object that *should* be mutable — `PortfolioPosition` in the main
`value-screener` backend is a plain class, not a record, precisely because `recordPurchase`/
`recordSale` genuinely need to mutate quantity and average price over the position's lifetime. The
lesson: **reach for a record when the concept is a fact/snapshot; reach for a regular class when the
concept has a lifecycle with real state transitions.** Getting this choice right up front saves you
from either (a) fighting a record's immutability with workarounds, or (b) accidentally allowing
mutation on something that should never change.

## Defensive copying with `List.copyOf`

`CompanyResearchResult` holds a `List<SourceReference> sources`. A naive record would do:

```java
public record CompanyResearchResult(..., List<SourceReference> sources) { }
```

This looks immutable, but it isn't, fully: the caller who passed in the list still holds a
reference to it. If they mutate their list after construction, your "immutable" record's list
changes too — the record's immutability is only skin-deep unless you defend against this.

The fix used in this project:

```java
public record CompanyResearchResult(..., List<SourceReference> sources) {
    public CompanyResearchResult {
        sources = List.copyOf(sources);
    }
}
```

`List.copyOf` does two things at once: it copies the elements into a new, independent list, **and**
that new list is itself immutable (calling `.add()` on it throws `UnsupportedOperationException`).
This is the difference between "immutable by convention" (please don't mutate this) and "immutable
by construction" (the JVM will throw if you try). Always prefer the latter for anything that
represents a domain fact.

## Factory methods for common construction patterns

`CompanyResearchResult.lowConfidence(...)` is a static factory method — not part of the record's
canonical constructor, just a regular `static` method on the record type that calls the canonical
constructor internally with a fixed `ConfidenceLevel.LOW` and typically an empty source list. This is
a common pattern once you notice the same "shape" of construction recurring in multiple places (here:
every guardrail failure path in `CompanyResearchAgent` needed to build a low-confidence result with
no sources) — instead of repeating the full constructor call with the same arguments everywhere,
name the pattern once. A good sign you need a factory method: when you find yourself writing a
comment like `// construct a low-confidence, no-sources result` above a constructor call more than
once.

## `ConfidenceLevel` — why an enum, not a `String` or `boolean`

A tempting shortcut would be a `boolean highConfidence` field, or a raw `String` like `"LOW"`. Both
are worse than an enum for the same reason: they don't make illegal states unrepresentable. A
`boolean` can only ever express two levels — if a third confidence tier is ever needed (this project
already reasons about "high confidence with a verified source" vs. "forced low confidence" as
conceptually different paths), you'd have to change the type everywhere. A `String` compiles fine
with a typo (`"Low"` vs `"LOW"`) and gives you no compiler help at all — an enum makes every valid
value a real, checkable identifier: the compiler rejects `ConfidenceLevel.LOWW` at compile time,
where a string typo would silently pass through as a bug waiting to happen at runtime.

## Takeaways for your own code

1. Reach for `record` when a type represents an immutable fact/snapshot; reach for a class when it
   has genuine lifecycle/state transitions.
2. Put invariant checks in the compact constructor — it's the one place that can never be bypassed.
3. Any collection field on an immutable type needs `List.copyOf`/`Set.copyOf`/`Map.copyOf` (or
   equivalent), not just "don't expose a setter" — otherwise immutability is an illusion.
4. Prefer enums over booleans/strings the moment a value represents one of a small, closed set of
   meaningful states — even if there are only two options today.
