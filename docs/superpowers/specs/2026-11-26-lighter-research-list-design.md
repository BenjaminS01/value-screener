# Design: lighter research list (Task 11) (2026-11-26)

## Why

`GET /api/research/snapshots` reads every snapshot item in full (5-10 KB each): ~13 RCU at 10 snapshots, ~128 at
100, while the table has 20 RCU. The research overview and the portfolio call it on every load. It must be live
before the live table holds about 30 snapshots; about 100 are expected long term.

## Goals / non-goals / anti-goals

- Goal: overview and portfolio read only small index items (~200 bytes per snapshot, ~3 RCU at 100).
- Goal: everything the overview shows today stays: grouping by group and by country, search (name, ticker,
  ISIN), "veraltet", "im Portfolio"; the portfolio still knows which ISINs are researched.
- Non-goal: changing the detail view (`GET /api/research/snapshots/{isin}`), the history or group assignment.
- Anti-goal: an index item that a concurrent save gets overwritten with older data.

## Approaches

1. **Extend the existing snapshot index and slim down the existing list endpoint** (chosen). The index items
   (`pk=SNAPINDEX`, `sk=ISIN#<isin>`) are already written in the same transaction as every save and used for group
   counts; they get the few fields the list needs. One endpoint, the expensive full read disappears.
2. A new endpoint next to the old one: rejected, the old expensive endpoint would stay with no caller.
3. A projection on the snapshot query: rejected, DynamoDB charges RCU for the full item size anyway.

## Design

- **Index item** gets `companyName`, `ticker`, `country` (absent when null), `groupSource` (with `groupKey`) and
  `lastResearchedAt` (newest finding `asOfDate`, absent when no findings). Written by every snapshot save as today.
- **`GET /api/research/snapshots`** returns summary rows from the index:
  `isin, ticker, companyName, country, group {key, name, source} | null, lastResearchedAt, stale`. `stale` is
  computed with `ResearchFreshness` as today. The `?group=` filter is dropped (no caller in the frontend).
  Rows are eventually consistent, like the group counts.
- **Backfill of existing data:** new marker `META / INDEX#snapshots-v2`. When absent, the SNAPSHOT partition is
  read once and the full index item is put per snapshot, with the condition "item does not exist or has no
  `companyName`" so an item written by a save is never overwritten. Marker written last (same pattern as v1).
- **Frontend:** new type `SnapshotSummary`; `fetchAllSnapshots` returns it; `ResearchOverviewPage`,
  `research/grouping.ts` and `PortfolioPage` use it. `CompanySnapshot` stays for the detail view.
- **Shipping:** backend and frontend test-first, both live with one `v*` tag (the contract changes).
- **Live check:** overview shows groups, countries, "veraltet", "im Portfolio"; portfolio opens researched
  positions.

## Left to the builder

Class and method names, where the summary mapping lives, test layout, removal of now unused repository methods
(`findAll`, `findAllByGroupKey`, `findAllWithoutGroup`) if nothing else uses them.

## Deutsche Kurzfassung

Der vorhandene kleine Index pro Snapshot bekommt Name, Ticker, Land, Gruppenquelle und Recherche-Datum. Die Liste
`GET /api/research/snapshots` liest nur noch diesen Index statt aller kompletten Snapshots (ca. 3 statt 128 RCU bei
100 Snapshots). Bestehende Daten werden beim Start einmal nachgefüllt (Marker v2), ohne neuere Einträge zu
überschreiben. Übersicht und Portfolio sehen gleich aus wie heute.
