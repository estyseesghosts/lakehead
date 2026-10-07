# Home grid repair Phase 9 — delete old compatibility machinery

Authority: `../../lakehead-grid-repair-plan.md` §3 (1–15), §5 Phase 9 (slices 9.1–9.5, exit gate), §9 non-goals.

Status: **complete (2026-10-07)**; exit gate met by search evidence (no deleted symbol or old-route marker in `*.kt`; remaining hits are durable "no row window / never rebased" rules). Device suites not run. Evidence in `../../../state-logs/handoffs/2026-10-07-grid-repair-phase9.md`.

## Ownership and rules

`:feature:home`, `:core:grid`, `:core:designsystem` only. Behavior unchanged. `GridRules` and `GridRowGapNormalizer` untouched. No Gradle edges. No lease/ack/request-id concept added. A symbol is deleted only if it has no production caller and a surviving test covers the replacement behavior; tests are deleted only if they exclusively exercise deleted symbols.

## Audited scope (before any deletion)

### 9.1 obsolete MOVE frame machinery
- `MoveCoordinateFrame` (`MoveDragMath.kt`): all four fields and `columns` are read by `MoveDragMath`; built from `DragState.Move` per `moveTo`. They are the live absolute-mapping inputs (contract 7), not superseded → **kept**.
- Frozen scroll baseline / structural re-freeze / live move-frame mirrors / `shouldRefreezeMoveFrame`: no code symbols remain (grep). Only comments → 9.5.
- Finite legality-row coupling: `normalLegalityRows` (`HomeGridGeometry.kt`) has no production caller (tests only). Covered by `HomeEditWorkspaceTest.candidate_atRow30_isIdenticalBeforeAndAfterWorkspaceGrowth` and `GridRules` tests. → **delete** with its 7 `legalityRows_*` tests in `HomeNormalViewportTest`.

### 9.2 edit-frontier legality machinery
- `HomeEditFrontier.kt` (79 lines) holds only auto-scroll constants/zone helpers; frontier legality and height logic are already gone (height lives in `HomeEditWorkspace.kt`). → rename object/file to `HomeAutoScroll` (no behavior change).
- `GridVerticalLayout.EditBoundary`, `createEditBoundary`, `advanceEditBoundary`, `MAX_WORKING_MARGIN_ROWS`: no production caller (tests only) → **delete** with their tests in `GridVerticalLayoutTest`. The `workingMarginRows` parameter of `measure` stays (validated call-boundary argument, many callers; revisit in Phase 10).

### 9.3 signed/rebased rendering support
- `WindowedSpanContainer.kt`: no production consumer in any module → delete, with `WindowedSpanContainerTest` (androidTest) and `WindowedSpanLayoutSignedOriginTest`.
- `GridRowWindow` / `GridVisiblePlacements`: only consumer is `WindowedSpanContainer` → delete with `GridVisiblePlacementsTest`, `GridRowWindowSignedTest`.
- `GridContentBounds`/`ContentBounds`: only production use is `contentBoundsForTiles` (itself unused) → delete with `GridContentBoundsTest`, `AbsoluteGridContentBoundsTest`.
- `SignedGridPlacement` + the signed branch of `GridVerticalLayout.measure`: no production producer → delete, with signed tests.
- `rendererOriginYPx`, `contentBoundsForTiles`, wrapper `contentMinHeightPx`: unused in production. `contentMinHeightPxSigned` is live (used by `committedFrameFor`); rename it to `contentMinHeightPx` replacing the wrapper.
- Kept: `signedRestFor`, `GridRest.gridOriginY` (live rest-offset math despite the name).
- To inspect before deleting: designsystem `ResizeCandidateOverlay`/`TileBounds` `rowOrigin`/`originYPx` parameters (production caller `CandidateOverlays.kt`).

### 9.4 lease machinery
- Already deleted in Phase 7: `PendingMoveCommit`, `moveCommitAck`, `HomeMoveCommitAck`, `moveGateOpen`, request/sequence IDs (grep over main and test source of `:feature:home`, `:app`, `:data:repository`, `:core:*` finds no code reference). Nothing to delete.
- `DragExit.STOLEN` / `HomeDragDiscard.STOLEN`: no production producer, but pager-arbitration, not lease; no Phase 9 slice names them → kept, recorded as follow-up.

### 9.5 forensic comments
- Stale: `HomeCanvasGrid.kt:125-126` (request id / ack), `HomeEditFrontierTest.kt:20,24`, `HomeCommittedScrollTest.kt:23`, plus whatever remains after the deletions above.

## Results

Deleted: `normalLegalityRows`; `GridVerticalLayout.EditBoundary`/`createEditBoundary`/`advanceEditBoundary`/`MAX_WORKING_MARGIN_ROWS`; `WindowedSpanContainer`; designsystem `ResizeCandidateOverlay` composable and `resizeCandidateFrame` (tag constant kept); `TileBounds.signedBounds`; `GridRowWindow`/`GridVisiblePlacements`; `GridContentBounds`/`ContentBounds`; `SignedGridPlacement` and the signed branch of `measure`; `rendererOriginYPx`; `contentBoundsForTiles`; `contentMinHeightPx` wrapper (`contentMinHeightPxSigned` renamed to `contentMinHeightPx`). `HomeEditFrontier` renamed `HomeAutoScroll`.

Kept: `MoveCoordinateFrame` (live fields), `signedRestFor`, `SignedPixelFrame`, `measure`'s `workingMarginRows` parameter and generic signature (Phase 10), `DragExit.STOLEN`/`HomeDragDiscard.STOLEN` (pager arbitration, follow-up), `HomeEditFrontierTest` class name (Phase 10).

Tests deleted (each exclusively covered deleted symbols): core:grid 54 (5 EditBoundary, 8 GridVisiblePlacements, 24 GridRowWindowSigned, 8 GridContentBounds, 7 AbsoluteGridContentBounds, 3 signed in GridVerticalLayoutTest; minus 1 added: `verticalMeasurementIgnoresColumnsAndNeverRejectsWideGridPlacements`); core:designsystem 14 unit (7 TileBoundsSigned, 5 WindowedSpanLayoutSignedOrigin, 2 ResizeCandidateOverlayFrame) + 19 androidTest (`WindowedSpanContainerTest`); feature:home 10 (7 `legalityRows_*`, `signedFrame_matchesLegacyRowOriginZeroFormula`, `rendererOriginYPx_passesThroughRestGridOriginY`, `contentBoundsForTiles_preservesAbsoluteLeadingRows`); the GridContentBounds half of one `HomeRenderProjectionTest` case was removed.

Unit counts (forced): `:core:grid` 130 (was 184), `:core:designsystem` 155 (was 169), `:feature:home` 537 (was 547), `:app` 108; 0 failures. `assembleDebug`, `lint`, unittest 15/0, `git diff --check` clean.
