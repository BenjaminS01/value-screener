# Design: remove the stock group history

Date: 2026-11-18. Status: short design (small change), approved by the user 2026-11-18.

## Intent and why

The research detail page shows a "Gruppenverlauf" (every group change of a stock). The user only needs the
current group; the history is of no interest. Removing it drops code, tests and one write per group change.
The finding history stays (user decision, 2026-11-18).

## Goals

1. No group history is written or read any more; the `groups` part of the history response and the
   "Gruppenverlauf" block in the frontend are gone.
2. Unchanged: the snapshot's group and group source (AI/MANUAL, so the AI import never overwrites a manual
   group), the finding history, delete protection of groups.

## Non-goals

- No change to the finding history, `HistoryOrigin`, the snapshot item, the group items or the API of groups.
- No clean-up of group history items already stored locally (nobody reads them; nothing is live yet).

## Approaches considered

| Variant | Trade-off | Outcome |
|---|---|---|
| **A. Remove the history; the service passes "group changed" explicitly to `save`** | delete protection (`markReferenced`) stays exactly where it is today, only its trigger changes | **proposed** |
| B. Mark the group as referenced on every save | simplest code, but every save writes the group item: concurrent saves of stocks in the same group collide (TransactionConflict → 409 during an import) | rejected |
| C. Keep writing the history, only hide it in the frontend | no backend change, but dead data and code remain | rejected |

## Design (variant A)

- Delete `StockGroupHistory`, `StockGroupHistoryRepository`, `DynamoStockGroupHistoryRepository`,
  `GroupHistoryItem` and `SnapshotHistoryView.GroupHistoryEntry`; `SnapshotHistoryView` keeps only `findings`.
- `CompanySnapshotRepository.save(snapshot, findingHistory, boolean groupChanged)`: when `groupChanged`, the
  transaction ends with `markReferenced` of the snapshot's group (position LAST, error mapping unchanged).
- `CompanySnapshotService`: `groupChanged` = the group key differs from the one before the upsert (a new
  snapshot counts as changed). A source-only change (AI → MANUAL, same key) no longer marks: that key was
  already marked when it was assigned.
- Frontend: remove `GroupHistoryEntry`, the `groups` field and the "Gruppenverlauf" block; tests adjusted.
- Phase 2b design gets a short note that the group history was removed (2026-11-18).
- The group delete conflict message no longer mentions a history: "Group X was assigned to a stock and cannot be
  deleted". The frontend no longer reloads the history after saving a group (a group change has no history).

## Constraints

- Full backend suite and frontend tests green. One commit; the user does all git operations.

## Left to the builder

- Which existing group-history tests are deleted and which become "group changed → marked" tests; the
  delete-protection tests must still pass unchanged.

## Deutsche Kurzfassung

Der Gruppenverlauf wird komplett entfernt (Backend, API-Feld `groups`, Frontend-Block „Gruppenverlauf“). Die
Gruppe an der Aktie samt Herkunft (KI/manuell) und der Kennzahlen-Verlauf bleiben. Der Löschschutz für benutzte
Gruppen bleibt erhalten: Statt am Verlaufseintrag hängt er künftig an einem einfachen „Gruppe hat sich
geändert“, das der Service an `save` übergibt.
