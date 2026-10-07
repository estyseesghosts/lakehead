# Home grid repair Phase 6 — transient edit workspace and simple edge auto-scroll

Authority: `../../lakehead-grid-repair-plan.md` §3 (contract items 6–9 are the anchors; 1–15 apply to every slice), §5 Phase 6 (slices 6.1–6.4), §9 non-goals.

Status: **implemented, unit- and device-tested (2026-10-06), uncommitted; plan gates 6.1–6.4 re-checked against code and met.** A closing device test now covers one auto-scroll step moving the candidate by exactly one row. Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase6.md`.

## Ownership

`:feature:home` only (presentation). Legality stays in `:core:grid` (`GridRules` untouched). `HomeGridMetrics` remains the single logical↔pixel owner. No Gradle edge was added or removed. The edit workspace is transient presentation state owned by `HomeCanvasGrid`; it is never persisted and never read by candidate or legality code.

## Serialized acceptance

1. **6.1 One workspace extent.** `HomeEditWorkspace.kt` holds pure functions for `workspaceBottomRow` (exclusive row count): initial value (committed bottom + one visible area + `LOOKAHEAD_ROWS`, capped by `MAX_HEIGHT_PX`), row-to-pixel mapping, coverage and never-shrinking extension. `HomeCanvasGrid` keeps one `workspaceBottomRow` state, set when a MOVE becomes active and cleared when it ends. It only sizes measured height (`minHeightPx`); candidates and legality never read it. Gate: a row-30 candidate is identical before and after the workspace changes (`HomeEditWorkspaceTest`, pure).
2. **6.2 Real scroll container grows.** While a MOVE is active the measured content height follows the workspace and the real `ScrollState.maxValue` follows that. There is no synthetic edit scroll maximum. Gate: a MOVE drag turns a non-scrolling grid into one with `maxValue > 0` and releases it afterwards (`HomeAbsoluteLayoutTest`, device).
3. **6.3 Simple edge auto-scroll.** `HomeEdgeAutoScroll.kt`: `nextAutoScrollTarget` (pure) and `runMoveEdgeAutoScroll` (dwell, proximity-scaled interval, extend workspace before each downward step, `scrollTo`, then remap the candidate from the unchanged pointer). No step budget, no scroll baseline, no copy of the frame. Scrolling never commits. Gate: a held bottom-edge drag grows `maxValue` and commits nothing until release (device).
4. **6.4 Stationary-pointer invariant.** Every update maps the pointer through the current scroll, so with a motionless pointer a scroll of exactly one row stride requests the next row. Gate: `HomeEditWorkspaceTest.stationaryPointer_scrollOneRowStride_requestsNextRow` (pure).

## Deleted

`initEditFrontier`, `advanceFrontierForMove`, `rebaseEditFrontier`, `editLayoutRows`, `editContentHeightPx`, `editScrollMaxPx`, `tickerScrollBaseline`, `shouldInitMoveFrontier`, `shouldUseEditLayout`, `contentHeightForLayout`, `shouldDiscardFrontierOnMoveEnd`, `moveScrollBaseline`, `scrollBaselinePx`, `onDragBegan`, the baseline-based steal check in `trackDrag`, the eight-change budget, and the old ticker.

## Behavior changes made deliberately

- The workspace is dropped when the MOVE ends, so the scroll range returns to the committed content before the loaded tiles update.
- `DragExit.STOLEN`, `HomeDragDiscard.STOLEN` and `resolveDragEnd` remain (with their tests) for Phase 7 but have no production producer now.

## Verification and exclusions

Per `AGENTS.md` §6 plus `:feature:home` device instrumentation. `test_home_canvas_grid_tripwire_size` stays red until Phase 8. Not done here (Phase 7): the acknowledgement lease (`PendingMoveCommit`, `moveCommitAck`, `moveGateOpen`) and the request-id callbacks. Device-verified (SM-G986W): `HomeAbsoluteLayoutTest.oneAutoScrollStep_withAMotionlessPointer_requestsExactlyTheNextRow` (scroll offset after the first step equals one row stride and the candidate's content-space top moves by the same stride, nothing committed). Not device-verified: a real finger holding the edge, and rows beyond ~1,400 (Compose measure limit; workspace capped at 200,000 px). `GridRules`, `FeedOverlayGatewayImpl.kt` untouched; no virtualization.
