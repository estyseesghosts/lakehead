# Home grid repair Phase 4 — replace gesture routing

Authority: `../../lakehead-grid-repair-plan.md` §3 (contract items 7, 8, 9, 12 are the anchors), §5 Phase 4 (slices 4.1–4.4), §9 non-goals.

Status: **implemented and unit/device-tested (2026-10-06), uncommitted.** Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase4.md`.

## Ownership

`:feature:home` only. The pointer loop and its pure end-resolution live in `HomeGridGestures.kt`; pager-versus-grid ownership lives in `HomePagerGate.kt`. Legality stays in `:core:grid`; persistence stays outside the feature. No Gradle edge was added.

## Serialized acceptance

1. **4.1 One pointer loop.** `homeDragGesture` reads down → hit test → slop → route once → update → one outward `HomeDragEnd` (commit intent or discard). MOVE and RESIZE share one tracking loop. **Deferred, not delivered:** the exit gate also names persistence and structural re-freeze. The acknowledgement lease and MOVE frontier remain behind `HomeDragHost.onDragBegan/onDragEnded` (retire in Phase 7); `rebaseMetrics` remains in `HomeDragHost.liveMetrics` (retire in 5.3/9.1). The loop does not follow them.
2. **4.2 MOVE routing.** Matrix proven in `HomeDragRoutingTest`: left/right/up/down, diagonal, 1×1/2×2/4×2, grab at centre and each corner, collision, row < 0 (raw `RequestedCell` boundary, not `LogicalPosition`), right-edge overflow, absolute-not-accumulated, scroll. Span is unchanged in every case.
3. **4.3 RESIZE routing.** Position unchanged across grow/shrink, collision, right-edge overflow, finish and cancel; unarmed handle routes NONE; a transfer drag never inherits the arm; a begun drag never toggles the arm.
4. **4.4 Pager arbitration.** `HomePagerGate` owns hold + axis lock; the drag only claims/releases the hold and never reads the axis lock. Integration proof: `HomeDragPagerArbitrationTest` (device).

## Behavior changes made deliberately

- The drag no longer cancels on a `pagerAxisLock` change (only a foreign vertical scroll steals).
- The drag reads tiles/metrics/callbacks fresh through `rememberUpdatedState` instead of closure values captured when the pointer scope restarted.

## Verification and exclusions

Per `AGENTS.md` §6, plus `:feature:home:connectedDebugAndroidTest` for 4.4. `test_function_audit.HomeCanvasGridTest.test_home_canvas_grid_tripwire_size` stays red until Phase 8 (do not lower the threshold). No rendering (Phase 5), scrolling/auto-scroll (Phase 6) or reconciliation (Phase 7) work; `FeedOverlayGatewayImpl.kt` untouched.
