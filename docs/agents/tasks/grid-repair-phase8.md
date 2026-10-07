# Home grid repair Phase 8 — decompose `HomeCanvasGrid.kt`

Authority: `../../lakehead-grid-repair-plan.md` §3 (1–15 apply to every slice), §5 Phase 8 (slices 8.1–8.6), §9 non-goals.

Status: **Phase 8 complete (2026-10-07): slices 8.1–8.6 done (8.4 was an audit with no code change, see `2026-10-07-grid-repair-phase8-slice4.md`; 8.5 see `2026-10-07-grid-repair-phase8-slice5.md`; 8.6 and the exit gate see `2026-10-07-grid-repair-phase8-slice6.md`).** Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase8.md` (8.1), `../../../state-logs/handoffs/2026-10-06-grid-repair-phase8-slice2.md` (8.2) and `../../../state-logs/handoffs/2026-10-07-grid-repair-phase8-slice3.md` (8.3).

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

## Slice 8.4 — layout extraction (done, no code moved)

- `HomeGridLayout.kt` (earlier phase) already owns child composition, measurement and placement: `HomeGridLayout` and the pure `homeGridContentHeightPx`. It consumes tiles, `HomeGridMetrics`, a `renderedSpan` lambda, `minHeightPx` and `tailPx`, and decides no legality or edit transition.
- `HomeCanvasGrid.kt` was audited: no `Layout`, `measure`, `place` or `layout(` remains; its only layout contact is the `HomeGridLayout(...)` call. `minHeightPx` (workspace-floor choice tied to `moveActive`) and `renderedSpanOf` (edit-state read) stay in the composable as edit-derived inputs, not layout.
- No symbols moved, no tests moved; counts unchanged. `HomeCanvasGrid.kt` 845 lines; `HomeCanvasGrid()` 621 lines.

## Slice 8.5 — overlay extraction (done)

- `CandidateOverlays.kt` (earlier phase) already owned `MoveCandidateOverlay`/`ResizeCandidateOverlay`; untouched.
- New `HomeGridOverlay.kt`: `HomeGridOverlay(...)` composable (move/resize candidate calls, resize handle, remove button, edit shortcut menu gate) moved verbatim from `HomeCanvasGrid()`, plus `resizePreviewSpan` (with KDoc). It reads state and calls callbacks only: `onToggleResizeArmed`/`onRemove` stay in `HomeCanvasGrid()` and perform the edit transitions and clear `moveEdgeXPx`/`moveEdgeYPx`. `scrollYPx` is read at the call site.
- Not moved: `moveEdgeXPx`/`moveEdgeYPx`, `minHeightPx`, `renderedSpanOf` (8.6).
- `HomeCanvasGrid.kt` 845 → 692 lines; `HomeCanvasGrid()` 621 → 502 lines. `ResizePreviewSpanTest` unchanged (same package); test counts unchanged.

## Slice 8.6 — reduce assembly file (done)

- Audit: `HomeCanvasGrid()` was already the assembly (scroll/edit/pager state, hold and selection emission, modifier wiring, forwarding commit intents). No editor transition logic, routing predicate or overlay rendering remains; what is left are one-line call-site transitions that sit with the state owner.
- Moved: `moveEdgeXPx`/`moveEdgeYPx` → `MoveEdgePointer` (new `MoveEdgePointer.kt`, one owner of the held MOVE pointer: `set`/`clear`/`position`, replacing six paired clears); `minHeightPx` derivation → `moveWorkspaceFloorPx` in `HomeEditWorkspace.kt` (pure; `workspaceBottomRow` state stays in the composable).
- Stays: `renderedSpanOf` (one-line `editState.renderedSpan` read passed to `HomeGridLayout`).
- Decision: the file and composable keep the name `HomeCanvasGrid` (no `HomeGrid.kt` rename): `HomeCanvas.kt`, KDocs and the audit tests reference it and a rename adds churn without changing responsibility.
- `HomeCanvasGrid.kt` 676 lines; `HomeCanvasGrid()` 487 lines (from 1,882). No test moved or edited.

## Phase 8 exit gate — met (2026-10-07)

- No new god file: largest new/extracted files are `HomeGridGeometry.kt` 593 (cohesive pure math, from 8.1), `HomeGridEditor.kt` 382, `HomeGridGestures.kt` 358, `HomeGridOverlay.kt` 217; none holds mixed responsibilities.
- `function_audit.py` reports every production function; the largest Home-grid function is `HomeCanvasGrid()` at 487 lines, far from the 1,882-line scale (`LauncherShell` 309 is the next largest).
- The 100–300 line aim is exceeded by Geometry/Editor/Gestures and the assembly; kept for cohesion per the plan.
- `test_home_canvas_grid_tripwire_size` deleted; unittest discovery 15 tests, 0 failures. The "largest function" audit test still holds.

Phase 8 complete. Phase 9 not started.

## Verification and exclusions

Per `AGENTS.md` §6 plus `:feature:home` and `:app` device instrumentation. Not done: Phase 9. Not device-verified: real-finger drag/edge hold, drop followed by a normalization shift, a possible one-frame flash when the loader `StateFlow` lags the drag-state clear.
