# Home grid repair Phase 7 — persistence reconciliation out of the grid

Authority: `../../lakehead-grid-repair-plan.md` §3 (1–15 apply to every slice), §5 Phase 7 (slices 7.1–7.4), §9 non-goals.

Status: **implemented, unit- and device-tested (2026-10-06), uncommitted.** Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase7.md`.

## Ownership

Reconciliation owner: `HomeRouteLoader` (`:app`), with pure rules in `HomePlacementReconciliation.kt` beside it. `:feature:home` only emits intents; `GridRules` and `GridRowGapNormalizer` are untouched. Normalization stays in repository mutation and load policy. No Gradle edge was added or removed.

## Serialized acceptance

1. **7.1 Feature callbacks.** The Home grid exposes `onMoveTile(ItemId, LogicalPosition)`, `onResizeTile(ItemId, LogicalSpan)` and `onRemoveTile(ItemId)`. No request id, lease, ack DTO or `moveCommitAck` crosses the boundary; drag state clears right after the intent is emitted. Gate: grep of `feature/home/src` for `PendingMoveCommit|moveCommitAck|HomeMoveCommitAck|moveGateOpen|requestId` is empty.
2. **7.2 Loader reconciles.** On release the loader applies the requested cell optimistically (synchronously, before the first suspension), serializes the repository write, and treats the returned normalized grid as authoritative. Rejected, NotFound, failure and cancellation reload the persisted state. The requested placement is never assumed to be the final layout.
3. **7.3 No round trip.** `MutationResult.Accepted` carries the full final grid; the last in-flight mutation replaces state from it directly (no write → reload → ack). A grid naming an unknown item falls back to a reload. An earlier mutation never reconciles while a later one is in flight; a remove only reloads when nothing else is in flight.
4. **7.4 Normalization tests.** `RoomHomePlacementRepositoryTest.moveResultSeparatesTheRequestedDropCellFromTheNormalizedFinalCells` shows a requested drop cell differing from the accepted normalized cells (the dropped tile itself moves, a distant one shifts), with persisted rows and a later load matching. The loader test `commitMoveAcceptedGridWinsOverTheRequestedCellWhenNormalizationMovesTheDroppedTile` shows the same at the state owner. Nothing normalizes during pointer updates.

## Deleted

`PendingMoveCommit` and lease helpers, `HomeMoveCommitAck`, `moveGateOpen`, `moveCommitAck`, request-id callback parameters, and their tests (ack tests in `HomeCommittedScrollTest`, pending-commit tests in `HomeEditFrontierTest`, lease tests in `HomePointerLoopRegressionTest`). `HomeDragDiscard`, `DragExit` and `resolveDragEnd` stay with their tests.

## Behavior changes made deliberately

- The dropped tile appears at its requested cell immediately; the old tests asserting reload-on-Accepted and no-reload-on-Rejected/NotFound for move and resize were rewritten because the contract is now the opposite: Accepted needs no reload, Rejected/NotFound/failure reload to undo the optimistic cell.
- No automatic retry; reload is the recovery.

## Verification and exclusions

Per `AGENTS.md` §6 plus `:feature:home` and `:app` device instrumentation. `test_home_canvas_grid_tripwire_size` stays red until Phase 8. Not done: any Phase 8 extraction or file split. Not device-verified: a real finger drop followed by a normalization shift, and a possible one-frame flash if the `StateFlow` update lags the drag-state clear.
