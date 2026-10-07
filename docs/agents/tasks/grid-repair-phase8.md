# Home grid repair Phase 8 — decompose `HomeCanvasGrid.kt`

Authority: `../../lakehead-grid-repair-plan.md` §3 (1–15 apply to every slice), §5 Phase 8 (slices 8.1–8.6), §9 non-goals.

Status: **Slices 8.1–8.3 implemented and verified (2026-10-06/07). Slices 8.4–8.6 not started.** Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase8.md` (8.1), `../../../state-logs/handoffs/2026-10-06-grid-repair-phase8-slice2.md` (8.2) and `../../../state-logs/handoffs/2026-10-07-grid-repair-phase8-slice3.md` (8.3).

## Ownership

`:feature:home` only. Pure moves with no behavior change; `GridRules`, `GridRowGapNormalizer` and the Gradle graph are untouched. `HomeGridMetrics` stays the single owner of the logical↔pixel mapping.

## Slice 8.1 — geometry extraction (done)

- `HomeGridGeometry.kt` already held `HomeGridMetrics`, `GridPixelRect`, `RequestedCell`, `homeGridMetricsFor`, `tileAt`. It now also owns the pure helpers moved verbatim out of `HomeCanvasGrid.kt`: `centeredOriginX`, `rendererOriginYPx`, `originTargetFor`, `shouldSnapOrigin`, `contentBoundsForTiles`, `signedRestFor`, `contentMinHeightPx(Signed)`, `contentContainerHeightPx`, `resizeHandleSidePx`, both `isInHandle`, `ControlCorner`, both `cornerControlOffset`, `committedScrollMaxPx`, `clampScrollToCommitted`, `isExtentParityOk`, `committedExtentOrRemeasured`.
- New pure `committedFrameFor` / `HomeCommittedFrame(extent, rest)` replaces the inline extent + natural-height + rest derivation inside `HomeCanvasGrid()`.
- Gate: no Compose state in the file (only `IntOffset`, a value type).
- Result: `HomeCanvasGrid.kt` 1387 → ~1090 lines; `HomeCanvasGrid()` 631 → 621 lines. The composable body barely shrinks in 8.1 because the bulk is editor, gesture and overlay wiring (8.2–8.5).

## Slice 8.2 — editor extraction (done)

- `HomeEditState.kt` renamed to `HomeGridEditor.kt` (same package `me.foxtails.lakehead.feature.home`, class unchanged).
- Moved verbatim from `HomeCanvasGrid.kt` into `HomeGridEditor.kt`: `isEditTransferDrag`, `releaseCommit`, `isHomeHold`, `gridHoldSignal`.
- Removed unused `GridRules` import from `HomeCanvasGrid.kt`; file 978 → 921 lines; `HomeCanvasGrid()` 621 lines (unchanged by this slice).
- Gate: moved functions are editor state/transitions, not Compose-layout or gesture-routing.

## Slice 8.3 — gestures extraction (done)

- `HomeGridGestures.kt` already owned the pointer loop (`homeDragGesture`, `homePagerGesture`, `resolveDragEnd`); the auto-scroll ticker already lived in `HomeEdgeAutoScroll.kt` (Phase 6). Both untouched.
- Moved verbatim from `HomeCanvasGrid.kt` into `HomeGridGestures.kt`: `DownDragRoute`, `routeDownDrag`, `transferDragRoute`, `PointerEndDecision`, `classifyPointerEnd`. Tests keep their count.
- Not moved: `moveEdgeXPx`/`moveEdgeYPx` (written by the drag host callbacks and cleared by exit/hide/back paths wired in the composable, read by the ticker lambda; a single owner already — moving them would only relocate); `resizePreviewSpan` (render preview, 8.5).
- `HomeCanvasGrid.kt` 921 → 845 lines; `HomeCanvasGrid()` still 621 lines.
- Gate: gestures file holds no persistence, row-window or renderer-rebasing code.

## Remaining (not started)

8.4 layout, 8.5 overlay (`HomeGridOverlay.kt`), 8.6 thin `HomeGrid.kt` assembly. Do in order; verify each. `test_home_canvas_grid_tripwire_size` stays until the plan gate is met; never lower its threshold.

## Verification and exclusions

Per `AGENTS.md` §6 plus `:feature:home` and `:app` device instrumentation. Not done: Phase 9. Not device-verified: real-finger drag/edge hold, drop followed by a normalization shift, a possible one-frame flash when the loader `StateFlow` lags the drag-state clear.
