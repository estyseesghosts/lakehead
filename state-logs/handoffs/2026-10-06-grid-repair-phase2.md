# 2026-10-06 — Home grid repair Phase 2: one geometry implementation (slices 2.1–2.6)
Status: complete (JVM/unit level only; no device verification)
Owner/slice: `:feature:home`; spec `docs/lakehead-grid-repair-plan.md` Phase 2.

## Baseline and worktree
HEAD `3b1c59a86f2b2614b28e5371d36f261738dcd463`. The worktree is dirty with ~115 modified/untracked paths (Phase 0–2 work plus concurrent/user work). Nothing is staged or committed. `:core:grid` and `:core:model` were not touched.

## Completed
- `HomeGridGeometry.kt` is the only owner of logical↔pixel transforms: `HomeGridMetrics` (`left/top/width/height/rect/contentY/requestedTopLeftCell/requestedSpan/signedFrame`, one snap rule `floor(offset/stride + 1e-3)`), `GridPixelRect`, `RequestedCell` (Long), `tileAt`, `homeGridMetricsFor`, `rowStrideCells`, and the row-window policies `visibleWindowFor`, `viewportRowsFor`, `normalLegalityRows` (moved here from `HomeCanvasGrid.kt`/`HomeEditFrontier.kt`; `rowOrigin` parameter dropped).
- MOVE is absolute and unclamped (`MoveDragMath`, `MoveCoordinateFrame(metrics, span, grabOffset…)`); an out-of-range request is `Rejected(RequestedCell)` and the candidate holds. RESIZE uses `ResizeDragMath.rawSpan(metrics, …)`. `shouldRefreshMoveFrame`-style re-freeze is now `shouldRefreezeMoveFrame(frame, metrics, span)`.
- 2.5 leftover: new `HomeEditSessionTest.resizeSequence_validBlockedAndOutOfBounds_neverChangesPosition` drives valid/blocked/out-of-bounds/shrink candidates and asserts `origin` and `lastValidPosition` equal the starting position. `HomeCanvasGrid.kt` resize release calls only `onResizeTile(itemId, span)` and re-emits the session with `origin = final.origin` (verified by reading ~1420–1471); it never writes a position.
- 2.6: every `TileBounds.signedBounds` call in `feature/home/src/main` is gone. Decision: `HomeGridMetrics.signedFrame(position, span)` converts `rect(...)` to the Int `SignedPixelFrame` at the boundary (rounding is exact: origins are whole pixels). Reason: `SignedPixelFrame` is a `:core:designsystem` type consumed by `isInHandle`, `cornerControlOffset`, `editShortcutMenuFrame`, swipe-surface placement; changing it would have broadened scope into designsystem, whereas the conversion keeps a single formula.
- `CandidateOverlays.kt` (new): `MoveCandidateOverlay` (moved out of `HomeCanvasGrid.kt`) and a feature-local `ResizeCandidateOverlay`, both taking `HomeGridMetrics` and sharing one outline composable. The designsystem `ResizeCandidateOverlay`/`resizeCandidateFrame` are no longer used by `:feature:home` (left in place; see follow-ups).
- `EditShortcutMenu` takes `metrics: HomeGridMetrics` instead of `geometry/originX/rowOrigin/originYPx`.
- `HomeCanvasGrid.kt`: `layoutMetrics` is built once before the stride is used; `rowStridePx` derives from it and `gridMetrics = layoutMetrics.copy(restOffsetYPx = originYPx)`. `rendererRowOrigin`, `rebasedOverlayTop` (no production caller — confirmed by grep) and the deprecated `HOME_CANVAS_ROW_ORIGIN` constant and `saturatingAdd` were deleted. `HomeCanvasGrid.kt` shrank (function_audit now reports it at 1,721 lines vs 1,838 at Phase 0).

## Per-test keep/retire decisions
- Retired (contract gone): clamp-to-edge MOVE tests → replaced by reject-and-hold tests; `rawSpan_zeroCellSize_returns1x1` → `homeGridMetrics_zeroCellSize_throws`; three old row-origin `ResizeDragMathTest` tests → absolute-row tests with a nonzero rest (+ `…sameStrideOffsetFromDifferentOriginRows_yieldsIdenticalSpans`); `frozenScroll_holdsRow` (compared two identical calls) → determinism test; `rendererRowOrigin_isAlwaysLogicalZero` and the two `rendererRowOrigin` asserts in `contentBoundsForTiles_…` (function deleted); `rebasedOverlayTop` tests and `RebasedOverlayTopTest.kt` (function dead and deleted); `VisibleWindowForTest.nonZeroOrigin_scrollZeroSelectsAbsoluteRows` (needed a `rowOrigin` parameter) → `scrolledToAbsoluteRow_selectsThoseRows` (scroll 500 shows rows 5–6).
- Kept/migrated (invariant still valid): `HomeCanvasSignedRendererTest.signedFrames_…` now `signedFrames_applyRestOffsetFromPermanentRowZero`; `ResizePreviewSpanTest.previewFrame_…` and `HomeCanvasOriginAnimTest` frame deltas now use `metrics.signedFrame`; `EditShortcutMenuComposeTest` (androidTest) call updated.
- Added: `HomeCanvasSignedRendererTest.signedFrame_matchesLegacyRowOriginZeroFormula` proves dropping `rowOrigin` is behavior-neutral by comparing `metrics.signedFrame` with `TileBounds.signedBounds(originRow = 0)` over rests {-216, 0, 540}, originX {0, 13}, several cells.

## Exit-condition grep
`grep signedBounds|rowOrigin|originYPx|originRow|\* rowStride|/ rowStride|rendererRowOrigin|rebasedOverlayTop` over `feature/home/src` leaves only:
- `HomeGridGeometry.kt` (the formulas themselves);
- `HomeCanvasGrid.kt`: the animated rest value named `originYPx` (a rest offset feeding `restOffsetYPx`, not a transform), and `rowOrigin = 0L` passed to the `:core:designsystem` `WindowedSpanContainer`, whose own row rebasing is Phase 5 (direct absolute placement);
- test files: a comment, and the parity test intentionally calling the legacy `TileBounds.signedBounds`.
No second logical↔pixel formula remains in `feature/home/src/main` outside `HomeGridGeometry.kt`. Sizing helpers (`centeredOriginX`, `contentMinHeightPxSigned`, `contentHeightForLayout`, `signedRestFor`, scroll-max helpers, corner/handle placement on pixel frames) were left as specified.

## Validation (exact commands, results)
- `./gradlew :feature:home:testDebugUnitTest --rerun-tasks --console=plain -q` (start of session): PASS, executed.
- `./gradlew :feature:home:testDebugUnitTest --console=plain -q` (after 2.6): PASS.
- `./gradlew :feature:home:test :core:grid:test --rerun-tasks --console=plain`: BUILD SUCCESSFUL, 50 tasks executed; `:feature:home:testDebugUnitTest` and `:core:grid:testDebugUnitTest` executed (new test present in `TEST-…HomeEditSessionTest.xml`).
- `./gradlew assembleDebug --console=plain -q`: PASS.
- `./gradlew test --rerun-tasks --console=plain`: BUILD SUCCESSFUL, 263 tasks executed, all module `testDebugUnitTest` tasks executed; `fixtures:launchable` is NO-SOURCE.
- `./gradlew :feature:home:compileDebugAndroidTestKotlin --console=plain -q`: PASS (androidTest sources compile; they were not run).
- `./gradlew lint --console=plain -q`: completed, wrote reports, no error output.
- `python3 tools/scripts/repo_map.py`, `module_graph.py` (graph unchanged: no new edges), `function_audit.py` (19 findings; `HomeCanvasGrid.kt:177` 1,721 lines — still a known large function, now smaller), `compose_audit.py` (3 pre-existing comment-text findings only): completed.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests OK (run after the edits).

## Not verified
Nothing in this phase was verified on a device or emulator. No instrumentation test (including `HomeCanvasTest`, `EditShortcutMenuComposeTest`) was run; real drag behavior, scroll interaction and overlay alignment on screen are unconfirmed.

## Follow-ups (logged, not fixed)
- The animated rest offset (`originAnim`) still moves under a drag and feeds `gridMetrics.restOffsetYPx` live; this violates contract item 6 (metrics immutable for an edit session). A later phase must freeze the rest at drag start.
- The MOVE re-freeze is a minimal bridge: it fires whenever the metrics or span differ from the frame's. The eventual owner is the authoritative `HomeEditState`.
- `:core:designsystem` `ResizeCandidateOverlay`, `resizeCandidateFrame`, `TileBounds.signedBounds` and `WindowedSpanContainer.rowOrigin` are now unused by `:feature:home` (except the container call with `0L`); deletion belongs to Phase 5/9.
- `HomeCanvasGrid.kt` is still 1,721 lines; decomposition is Phase 3/8.

## Worktree/commit state
HEAD unchanged at `3b1c59a`; all Phase 0–2 changes uncommitted; nothing staged. Files touched this session in `feature/home`: `HomeGridGeometry.kt`, `CandidateOverlays.kt` (new), `HomeCanvasGrid.kt`, `HomeEditFrontier.kt`, `EditShortcutMenu.kt`; tests `HomeEditSessionTest`, `HomeCanvasSignedRendererTest`, `HomeCanvasOriginAnimTest`, `ResizePreviewSpanTest`, `VisibleWindowForTest`, androidTest `EditShortcutMenuComposeTest`; `RebasedOverlayTopTest.kt` deleted.

## Next action
Slice 3.1: introduce the authoritative `HomeEditState` in `:feature:home` per the plan's Phase 3. Exit condition: one state object owns the edit session, the frozen metrics and the candidate, with the pointer loop reading and writing only through it, and unit tests proving the session cannot change position during RESIZE and the metrics do not change during a drag.
