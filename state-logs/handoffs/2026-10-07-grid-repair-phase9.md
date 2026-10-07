# Handoff — Home grid repair Phase 9 (2026-10-07) — IN PROGRESS

## Scope and baseline
- Objective: Phase 9, delete old compatibility machinery (slices 9.1–9.5 + exit gate) per `docs/lakehead-grid-repair-plan.md` §5. Task spec: `docs/agents/tasks/grid-repair-phase9.md`.
- Baseline: HEAD `747b1b9`, uncommitted Phase 3.2–8.6 worktree preserved. Unit baseline `:core:grid` 184, `:feature:home` 547, `:app` 108; device 82/0 and 33/4-known; unittest 15/0.
- Rule: delete only symbols with no production caller and a named covering invariant test.

## Slice log
- 9.1 DONE: deleted `normalLegalityRows` (HomeGridGeometry.kt) + 7 tests `legalityRows_*` in HomeNormalViewportTest (covering behavior: HomeEditWorkspaceTest.candidate_atRow30_isIdenticalBeforeAndAfterWorkspaceGrowth + GridRules tests). `MoveCoordinateFrame` kept (4 live fields). Frozen-baseline/re-freeze/live-mirror symbols: none left in code (comments only → 9.5).
- 9.2 DONE: deleted `GridVerticalLayout.EditBoundary/createEditBoundary/advanceEditBoundary/MAX_WORKING_MARGIN_ROWS` + 5 tests (editMarginIsCappedBeforeItExtendsTheBoundary, editBoundaryAdvancesOnlyAtTheTrailingMargin, repeatedAdvancesAreBoundedOneMarginAtATime, advancingDirectlyConstructedBoundaryClampsMalformedMargin, editBoundaryUsesExactArithmeticAtLongOverflow) + unused helper assertArithmeticException. `HomeEditFrontier` object/file renamed `HomeAutoScroll` (no logic change). `measure`'s `workingMarginRows` param kept (Phase 10).
- Unit after 9.1+9.2 (forced): `:core:grid` 179, `:feature:home` 540, `:app` 108, 0 failures.
- 9.3 DONE: deleted `WindowedSpanContainer`, designsystem `ResizeCandidateOverlay`/`resizeCandidateFrame`, `TileBounds.signedBounds`, `GridRowWindow`/`GridVisiblePlacements`, `GridContentBounds`, `SignedGridPlacement`, signed branch of `measure`, `rendererOriginYPx`, `contentBoundsForTiles`, `contentMinHeightPx` wrapper (signed variant renamed). Added test `verticalMeasurementIgnoresColumnsAndNeverRejectsWideGridPlacements` to keep the wide-column rule covered. Test deletions listed in the task spec.
- 9.4: nothing to delete (lease/ack removed in Phase 7). `DragExit.STOLEN`/`HomeDragDiscard.STOLEN` kept as follow-up.
- 9.5 DONE: stale comments fixed in HomeCanvasGrid, HomeGridGestures, HomeGridGeometry, GridVerticalLayout, HomeCommittedScrollTest, ResizeDragMathTest, EditControlPlacementTest, HomeCanvasSignedRendererTest.

## Final status: COMPLETE
- Validation: forced unit `:core:grid` 130, `:core:designsystem` 155, `:feature:home` 537, `:app` 108, 0 failures; `assembleDebug` PASS; `lint` PASS; `:core:designsystem:compileDebugAndroidTestKotlin` PASS; unittest discover 15/0; `git diff --check` clean; module_graph.py no new edges; function_audit/compose_audit show no new flags of note.
- Exit-gate grep: no deleted symbol or old-route name (lease/ack/request id/pendingMove/frozen baseline/re-freeze) in `*.kt`; remaining "row window"/"rebased" hits are durable negative rules.
- Not run: `:data:repository:test` (untouched), device suites (`:feature:home` 82/0 and `:app` 33/4 baselines), real-finger drag/edge hold, drop-then-normalization shift, possible one-frame flash.
- Worktree: uncommitted, nothing staged.
- Next action: run device suites on R5CN70E2SGV to confirm 82/0 and 33/4-known, then scope Phase 10.
