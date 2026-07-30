# Data Provider Client — Design

Last updated: 2026-07-29
Status: Design in progress (brainstorming). Architecture direction and provider cost research settled;
data model, error handling, and testing sections not yet drafted.

## Deutsche Zusammenfassung

Der Data Provider Client versorgt die Screening Engine und die Portfolio-Überwachung mit Fundamental-
und Kursdaten. Kernidee: **Zwei-Geschwindigkeiten-Refresh** — Fundamentaldaten (Gewinn/Aktie, Buchwert/
Aktie, Verschuldungsgrad, Margen, ROE, Aktienanzahl) ändern sich nur quartalsweise und werden entsprechend
selten (rotierend, z. B. 50–100 Ticker/Tag) aktualisiert; der Kurs ändert sich täglich und wird für das
gesamte bekannte Ticker-Universum täglich abgerufen. KGV, KBV und Marktkapitalisierung berechnet die
Screening Engine selbst aus (aktueller Kurs) × (gespeicherte Fundamentaldaten) — dadurch braucht kein
Anbieter einen nativen Marktweiten-Screener oder Market-Cap-Filter zu haben. Dieselbe Kriterien-Auswertung
läuft sowohl über das breite Marktuniversum (→ Kaufkandidaten/"Vorschläge") als auch über die gehaltenen
Portfolio-Positionen (→ Verkaufs-Hinweis, wenn eine Position zu teuer geworden ist oder die Fundamentaldaten
sich verschlechtert haben) — eine Berechnungs-Pipeline, zwei Ansichten.

Bei der Anbieter-Recherche zeigte sich: es gibt **keine kostenlose Lösung für globale Kursdaten** und für
Deutschland/Schweiz auch keine kostenlose offizielle Fundamentaldaten-Quelle — dafür wäre ein bezahlter
Anbieter (EODHD, ~€80/Monat für globale Abdeckung) nötig gewesen. Das wurde dem Nutzer als zu teuer
eingeschätzt (Investitionsvolumen ~1.000 €/Monat). **Entscheidung (2026-07-29): Start bewusst nur mit den
USA**, komplett kostenlos über SEC EDGAR (Fundamentaldaten) + FMPs Free-Tier (nur für den Kurs, 250
Calls/Tag) — dadurch entstehen aktuell **keine** Datenkosten. Andere Märkte (Japan, UK, Frankreich
kostenlos möglich; Deutschland/Schweiz weiterhin nur bezahlt) bleiben als spätere Erweiterung offen, u. a.
weil der Nutzer über Gettex auch nicht-US-Positionen hält, die vorerst nicht überwacht werden.

---

## 1. Purpose

The Data Provider Client supplies fundamental and price data to the Screening Engine and the portfolio
monitoring path (main spec, Section 4). It is the component addressing Risk 5 from the main design spec
(`docs/superpowers/specs/2026-07-21-value-screener-design.md`, Section 8): the fundamentals-data provider
must stay swappable, not hard-coupled to one vendor.

## 2. Architecture: two-speed refresh

Rationale (established during brainstorming, 2026-07-29): a valuation ratio like KGV (P/E) = Price ÷
Earnings changes mostly because of price movement, not earnings movement — earnings only change on a
company's own reporting cadence (quarterly). The same applies to KBV (P/B, via book value/share) and to
market capitalization (Price × shares outstanding, where shares outstanding also changes rarely). This
means the app does **not** need a provider-side "screener" or "market cap filter" endpoint at all — it can
compute KGV, KBV, and market cap itself, as long as it separately tracks two different refresh cadences:

- **Fundamentals** (EPS, book value/share, ROE, margins, debt/equity, current ratio, sector, shares
  outstanding): refreshed rarely, per ticker, rotating through a manageable batch (e.g. 50–100 tickers/day)
  so the whole known universe cycles roughly every 1–2 months — matching real-world quarterly reporting
  frequency. Stored as the existing `FundamentalSnapshot` entity (main spec, Section 6), taken on refresh,
  not necessarily daily for every ticker.
- **Price**: refreshed **daily**, for the entire known ticker universe plus all portfolio holdings. This
  is the frequent, high-volume path and the one that actually constrains provider choice (see Section 4).
  Requires a small new piece of state not yet in the main data model: "current price + as-of date" per
  ticker, tracked separately from the (slower-changing) `FundamentalSnapshot`.

The Screening Engine (main spec, Section 4) computes KGV/KBV/market cap and the branch-relative median
purely from data already in the app's own database — no additional provider call. It runs the **same**
Buffett-criteria evaluation over two audiences from one pass:
- Tickers **not** currently held, passing the criteria → feed into `Suggestion` (buy candidate).
- Tickers **currently held**, no longer passing the criteria (valuation became expensive, or fundamentals
  deteriorated) → feed into a sell-signal alert, extending the existing `FundamentalAlert` concept (main
  spec, Section 6), which previously only covered raw fundamental-metric changes, not a valuation-based
  trigger.

Universe scope, per the user's actual investment goal (not a fixed index list like the S&P 500, which
would only contain already-well-known names): broad, across large **and** mid-cap stocks, with only a
market-cap **floor** to exclude micro-caps (risk reasons) — no upper cutoff, since large, well-known
opportunities should still surface. The universe is built from a "list all tradable symbols per
exchange/country" endpoint (most providers offer this for free) and grown incrementally, not pre-curated
by hand. **Initial scope is USA-only** (Section 4.4); worldwide was the original ambition and remains the
long-term goal, added market by market as free sources allow.

## 3. Why this app never needs the vendor's own "screener" feature

Explicitly confirmed during brainstorming (see Section 4 for the market-by-market provider findings): a
native, provider-side market-wide screener with financial-ratio/market-cap filtering is rare on affordable
tiers, and unnecessary given Section 2's approach. This reopens providers that were initially ruled out
for lacking a screener (e.g. Twelve Data, Finnhub) as candidates purely for their fundamentals/quote
endpoints — though several were separately disqualified on cost or ToS grounds (Section 4).

## 4. Provider and cost research (2026-07-29)

Two separate needs, researched independently: (A) free/cheap **official bulk fundamentals** sources per
market, and (B) commercial providers for **daily price/quote** data (needed everywhere, since no free
official source provides live/daily price data) and for markets with no free fundamentals option.

### 4.1 Free, official, structured bulk fundamentals sources (per market)

| Market | Free official source | Usability | Verdict |
|---|---|---|---|
| USA | **SEC EDGAR** (`companyfacts.zip`, `data.sec.gov`) — no API key, no ToS ambiguity (public government data) | Excellent — clean XBRL-tagged JSON, ready to compute EPS/book value/ROE/etc. | Use as-is, fully free |
| Japan | **EDINET API v2** (Japan FSA) — free, requires only a free API-key registration | Good — structured CSV/XBRL export (`type=5`), ~5,000+ issuers, 10 years history | Free, official — but official docs/portal are **Japanese-only** (implementation effort, not a cost problem) |
| UK (listed companies) | **Companies House** free bulk accounts data + **UK-SEF** filings indexed via `filings.xbrl.org` | Good for large/listed issuers; ~25% of accounts are paper-filed (missing from the feed) and many small companies file abbreviated/micro-entity accounts lacking full P&L detail | Free for large/listed UK issuers; gaps for small/private companies are a data-completeness limit, not a cost one |
| France | `filings.xbrl.org` (aggregates ESEF/XBRL filings) / AMF's own `info-financière` API (document index, needs own parsing) | Good for large/mid-cap ESEF filers; weaker for smaller issuers | Free for large/mid caps |
| EU generally | **ESAP** (European Single Access Point, run by ESMA) — Phase 1 launches **10 July 2026**, full rollout by **10 July 2027** | Not live yet | Use `filings.xbrl.org` as an interim free substitute now; ESAP is a natural future replacement once live — build the provider interface so this swap doesn't require a rearchitecture |
| **Germany** | **None found** — Unternehmensregister/Bundesanzeiger has no bulk API, only one-filing-at-a-time downloads | N/A | **No free option; needs a paid provider** |
| **Switzerland** | **None found** — SIX Swiss Exchange/FINMA have no structured bulk disclosure equivalent | N/A | **No free option; needs a paid provider** |

### 4.2 Commercial providers (needed for: daily price/quote everywhere, and fundamentals for Germany/Switzerland)

| Provider | Relevant tier & price | Coverage | ToS/legal notes | Verdict |
|---|---|---|---|---|
| **EODHD** | "EOD Historical Data – All World": €19.99/month (price/quote, batch-by-exchange). "Fundamentals Data Feed": €59.99/month (batch-by-exchange). Combined: ~€80/month | Confirmed global, 60+ exchanges | Must delete cached data within 1 month of cancelling; "Non-Professional User" public-display clause is ambiguous for a publicly-hosted personal project — **recommend confirming with EODHD support before committing** | Most concretely verified option; the €19.99 price tier is needed regardless (no free daily price source exists anywhere) |
| **Financial Modeling Prep (FMP)** | Free: 250 calls/day, **confirmed US-only**. Starter ~$14/month: international scope unconfirmed. "Ultimate" tier (price unconfirmed) needed for bulk delivery + full global | US-only confirmed at free tier; higher tiers unclear | Requires a separate "Data Display and Licensing Agreement" for displaying/redistributing data — unverified whether a personal showcase site needs this | Not recommended as the primary global answer; free tier only useful for a US-only fallback |
| **Finnhub** | Free: 60 calls/min, US-only fundamentals/quotes. International tier cited inconsistently across sources ($12–$99/month) | Global claimed, US-only confirmed on free tier | Personal use only unless written approval; must delete data at subscription end; redistributing "derived results" needs written approval — ambiguous for a public site | Price and ToS interpretation unverified — would need a live signup + support inquiry before relying on it |
| **Twelve Data** | Free: 800 calls/day, US-only, no fundamentals. Fundamentals from "Grow" ($29/month) | Global claimed | **Disqualifying**: all "Individual" plans (including paid ones) prohibit public/external display of data. A public site needs the **Business "Venture" plan at $149/month** | Ruled out for a publicly-hosted app at any Individual-tier price point |

### 4.3 Ruled out (not cost-effective or not usable at any reasonable price)

Yahoo Finance/yfinance (illegitimate — ToS violation, scraping, personal-use-only, real legal risk for a
public app), Intrinio (~$3,000/year+), Marketstack (free tier too low, no structured fundamentals at all),
Polygon.io/Massive (US-only at every tier checked), Tiingo (US-only free tier, weak fundamentals), Nasdaq
Data Link/Quandl (US-focused, wrong product shape for this use case), StockData.org (no fundamentals data
at all), Norgate Data (desktop-only application, architecturally incompatible with a server-hosted Spring
Boot app), Wisesheets (launched days before this research; international coverage is roadmap-only, not
shipped).

### 4.4 Cost constellations considered, and the decision

1. **All-EODHD** (~€80/month): one provider, one integration, one data format. Rejected — too expensive
   relative to the user's ~€1,000/month investable budget, especially since EODHD's Fundamentals Data Feed
   appears to be sold as a flat global subscription with no cheaper per-market (e.g. DE/CH-only) option —
   meaning the "free-where-possible" savings for other markets would not materialize even if built (see
   superseded option 2 below), but the flat €80/month cost is still real and was judged not worth it.
2. ~~Free-where-possible + EODHD for the gaps~~ — superseded by the decision below. Would have added four
   separate source integrations (SEC EDGAR, EDINET, Companies House/UK-SEF, filings.xbrl.org) for no
   verified cost saving over option 1, since Germany/Switzerland still force the same flat EODHD
   subscription.
3. **Decided: start USA-only, fully free, defer other markets** (2026-07-29). Rather than paying for global
   coverage (option 1) or building five source integrations to avoid paying (option 2), scope the initial
   build to the single market with the best free tooling: **SEC EDGAR** for fundamentals (Section 4.1) +
   **FMP's free tier** (250 calls/day) for the daily price/quote refresh. FMP is used **only** for price
   here, deliberately not for its fundamentals product — this avoids relying on FMP's ambiguous "Data
   Display and Licensing Agreement" clause (Section 4.2) entirely, since the app never displays FMP's raw
   data, only ratios it computes itself from FMP's price + SEC EDGAR's fundamentals. FMP's free tier is
   confirmed US-only (Section 4.2), which is exactly this phase's scope — no international-coverage
   ambiguity applies. Cost: **€0/month**.
   - **Known limitation, accepted for now**: the user's existing portfolio includes non-US positions
     (bought via Gettex). Those positions will not get fundamentals/price refresh or sell-signal alerts
     until a later phase adds their market. To be surfaced clearly in the UI (which positions are
     currently monitored vs. not), not silently ignored.
   - **Future expansion path**: Japan (EDINET), UK (Companies House/UK-SEF), and France (filings.xbrl.org)
     remain available as free additions later; Germany/Switzerland remain paid-only (EODHD) whenever that
     expansion happens. ESAP (Section 4.1) may change the Germany/EU picture from mid-2026 onward — worth
     re-checking before paying for EODHD even at that point.

## 5. Open items (to resolve before or at the start of implementation)

1. Provider constellation for the initial USA-only scope is decided (Section 4.4: SEC EDGAR + FMP free
   tier), but neither has been verified with a real, live API call yet — still needs the same
   live-signup-and-test verification already planned for Risk 1 in the main design spec, e.g. confirming
   FMP's free-tier quote endpoint shape/format and rate-limit behavior in practice.
2. When a future market is added, EODHD's and Finnhub's ToS ambiguity around "personal but
   publicly-hosted" use would need resolving (recommend emailing provider support directly for a written
   answer) — not relevant to the current USA-only scope, since neither is used yet.
3. Whether EODHD's Fundamentals Data Feed can be scoped/billed to specific markets (e.g. only DE/CH) or is
   an all-or-nothing global subscription — only relevant again once/if Germany or Switzerland coverage is
   revisited; not blocking for now.
4. ESAP's Phase 1 launch (10 July 2026) is imminent relative to this design's timing — worth re-checking
   before ever paying for EODHD to cover the EU, whenever that expansion is revisited.

## 6. Not yet drafted

Data model changes (current-price tracking, universe/refresh-due tracking), error handling, rate-limit/
circuit-breaker behavior, and testing approach have not been discussed yet and are not covered by this
document version.
