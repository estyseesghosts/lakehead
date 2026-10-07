# Handoff — Home grid repair Phase 8, Slice 8.5 (2026-10-07)

## Completed (factual)
- Audit: `HomeGridOverlay.kt` did not exist. `MoveCandidateOverlay`/`ResizeCandidateOverlay` already lived in `CandidateOverlays.kt`, so they were not moved. What remained in `HomeCanvasGrid()` was the overlay wiring (candidate calls, resize handle, remove button, edit shortcut menu) and `resizePreviewSpan`.
- New `feature/home/.../HomeGridOverlay.kt`: `HomeGridOverlay(...)` and `resizePreviewSpan` (verbatim, with KDoc). The overlay takes read-only state (`moveDrag`, `resizeDrag`, `editState`, `tiles`, `gridMetrics`, `reducedMotion`, `dragging`, surface entries and owner id, available width/height, `scrollYPx`) and callbacks (`onToggleResizeArmed`, `onRemove`, `onSurfaceExecute`, `onSurfaceTogglePin`). It makes no legality decisions and no edit transitions.
- `HomeCanvasGrid.kt` calls it. The transitions stay in the call-site lambdas (`toggleResizeArmed` behind the `selectedTileId` guard; `onRemoveTile`, `onTileRemoved`, clearing `moveEdgeXPx`/`moveEdgeYPx`). `HomeCanvasGrid.kt` 845 → 692 lines; `HomeCanvasGrid()` 621 → 502 lines. Unused imports removed.
- Implementation by a `four` subagent (one bounded move + compile); I reviewed the full diff against the original block. One deviation: the redundant `val resizeDrag = editState.resizeDrag` became the parameter (same value, passed at the call site).

## Touched paths
`feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeGridOverlay.kt` (new), `HomeCanvasGrid.kt`, `docs/agents/tasks/grid-repair-phase8.md`, `state-logs/CURRENT.md`, this handoff. No tests edited, moved or deleted (`ResizePreviewSpanTest` compiles unchanged, same package).

## Decisions
- Overlay is cohesive as rendering-from-state; transitions stay with the state owner (the composable), since 8.6 owns assembly state.
- `moveEdgeXPx`/`moveEdgeYPx`, `minHeightPx`, `renderedSpanOf` left in place on purpose.
- No Gradle edges, `GridRules`/`GridRowGapNormalizer` untouched, tripwire untouched.
- `function_audit.py` now flags `HomeGridOverlay` (164 lines) as a review signal: it is one cohesive render layer; audits are signals, not triggers.

## Validation (each run separately)
- `./gradlew :feature:home:compileDebugKotlin`: PASS (subagent).
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks`: PASS; result XML sums `:core:grid` 184, `:feature:home` 547, `:app` 108, 0 failures.
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS.
- `module_graph.py`: no new edges. `repo_map.py` ran. `compose_audit.py`: no flags on the changed files. `function_audit.py`: `HomeCanvasGrid()` 502 lines, `HomeGridOverlay` 164.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests, 1 failure, the accepted tripwire (`502 not greater than 1500`); threshold untouched.
- `git diff --check`: initially flagged a blank line at EOF of `HomeCanvasGrid.kt`; fixed, now clean.
- Device SM-G986W (R5CN70E2SGV), woken and keyguard dismissed: `:feature:home:connectedDebugAndroidTest` 82 tests, 0 failures. `:app:connectedDebugAndroidTest` (two shell classes excluded) 33 tests, exactly the 4 known failures (`MainActivitySeededHomeDeviceTest` ×3 30 s timeouts; `WorkProfileLaunchProofTest` work profile 150 missing). Gradle device runs were not repeated after the final whitespace-only EOF fix.

## NOT run / not verified
Real-finger drag and edge hold; drop followed by a normalization shift on device; possible one-frame flash from loader `StateFlow` lag.

## Worktree
HEAD `747b1b9`; all changes (8.4 docs, 8.5 code and docs) uncommitted and unstaged.

## Next action
Slice 8.6: thin `HomeGrid.kt` assembly (it takes `moveEdgeXPx`/`moveEdgeYPx`, `minHeightPx`, `renderedSpanOf`); then delete `test_home_canvas_grid_tripwire_size` only if the plan §5 Phase 8 exit gate is met. Exit: assembly file thin, unit counts 184/547/108, compile/lint green, device 82/0 and 33/4-known, tripwire deleted only with the gate evidenced.
