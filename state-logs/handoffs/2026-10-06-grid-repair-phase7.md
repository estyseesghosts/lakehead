# Handoff — Home grid repair Phase 7 (2026-10-06)

## Completed (factual)
Slices 7.1–7.4 of `docs/lakehead-grid-repair-plan.md` §5 Phase 7, spec in `docs/agents/tasks/grid-repair-phase7.md`.
- 7.1: Home grid callbacks are `onMoveTile(id, position)`, `onResizeTile(id, span)`, `onRemoveTile(id)`. The lease, `moveCommitAck`, `HomeMoveCommitAck`, `moveGateOpen` and request ids are gone from `feature/home/src/main` (grep clean).
- 7.2/7.3: `HomeRouteLoader` owns reconciliation. Optimistic apply before the first suspension, `writeMutex` serializes writes, the last in-flight mutation replaces state from the accepted normalized grid with no reload; Rejected/NotFound/exception/cancellation/unknown-id grid reload. A remove reloads only when nothing else is in flight.
- 7.4: repository and loader tests separate the requested drop cell from the accepted normalized cells. Nothing normalizes during pointer updates. `GridRules` and `GridRowGapNormalizer` untouched.

## Touched paths
Added: `app/src/main/java/me/foxtails/lakehead/HomePlacementReconciliation.kt`, `app/src/test/.../HomePlacementReconciliationTest.kt`, `docs/agents/tasks/grid-repair-phase7.md`.
Edited: `app/.../HomeRouteLoader.kt`, `LauncherShell.kt`, `MainActivity.kt`; `feature/home/.../HomeCanvasGrid.kt`, `HomeCanvas.kt`, `HomeGridGestures.kt`, `HomeEditFrontier.kt`; tests `HomeRouteLoaderTest.kt`, `HomeCommittedScrollTest.kt`, `HomeEditFrontierTest.kt`, `HomePointerLoopRegressionTest.kt`, `RoomHomePlacementRepositoryTest.kt` (+1), androidTest lambdas in `HomeCanvasTest`, `HomeDragPagerArbitrationTest`, `HomeAbsoluteLayoutTest`.

## Decisions
- Optimistic apply in the loader so the dropped tile does not snap back once the lease is gone.
- `withAcceptedGrid` returns null for an unknown id; the loader reloads (an `AppPlacement` cannot be built from grid data).
- No retry; reload undoes the optimistic state.
- Loader tests for move/resize reload behavior were rewritten deliberately (contract inverted); tests of deleted lease/ack functions were deleted.

## Validation (each run separately, 2026-10-06)
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks --console=plain`: PASS. XML summed: `:core:grid` 184, `:feature:home` 547 (562 − 15 deleted), `:app` 108 (94 + 14), 0 failures.
- `./gradlew :data:repository:testDebugUnitTest`: PASS (includes new 7.4 test).
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS (reports written).
- `./gradlew :feature:home:connectedDebugAndroidTest`: SM-G986W 82 tests, 0 failures.
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=...ShellForwardingTest,...ShellPickerTest`: 33 tests, 4 failures, the same pre-existing set (`MainActivitySeededHomeDeviceTest` ×3 timeouts, `WorkProfileLaunchProofTest` work profile 150 missing).
- `repo_map.py`, `module_graph.py` (no new edges), `function_audit.py` (`HomeCanvasGrid` 631 lines), `compose_audit.py` (only old comment hits): run. `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests, 1 failure, the accepted tripwire (threshold untouched). `git diff --check`: clean.

## Unverified / known gaps
- Real-finger drop followed by a normalization shift: not run. No device test covers drop → normalization shift.
- Possible one-frame flash if the loader's `StateFlow` update lags the drag-state clear: not device-verified.
- Real-finger edge hold: not run.

## Worktree
Dirty with uncommitted Phase 3.2–7 work on HEAD `cb3d38f`. Nothing staged or committed.

## Next action
Phase 8 Slice 8.1: extract geometry from `HomeCanvasGrid.kt` into `HomeGridGeometry.kt`. Exit: no behavior change, unit and device suites at the numbers above, and `HomeCanvasGrid()` shrinks.
