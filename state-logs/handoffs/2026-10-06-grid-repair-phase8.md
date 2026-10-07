# Handoff — Home grid repair Phase 8, Slice 8.1 (2026-10-06)

## Completed (factual)
Slice 8.1 only (`docs/lakehead-grid-repair-plan.md` §5 Phase 8; spec `docs/agents/tasks/grid-repair-phase8.md`). Slices 8.2–8.6 were not started.
- Moved the pure geometry helpers verbatim from `HomeCanvasGrid.kt` into `HomeGridGeometry.kt` (list in the task spec). `HomeGridMetrics` was already there and remains the single mapping owner.
- Added pure `committedFrameFor`/`HomeCommittedFrame`, replacing the inline extent/natural-height/rest derivation in `HomeCanvasGrid()`.
- Removed imports orphaned by the move from `HomeCanvasGrid.kt`.
- `HomeCanvasGrid()` 631 → 621 lines (function_audit); file 1387 → ~1090.

## Touched paths
Edited: `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvasGrid.kt`, `HomeGridGeometry.kt`. Added: `docs/agents/tasks/grid-repair-phase8.md`, this handoff. Edited: `state-logs/CURRENT.md`. Temporary `/tmp/move_geometry.py` (outside repo).

## Decisions
- Moved code unchanged (no behavior change); no test edits, so test counts equal the baseline.
- Left `resizePreviewSpan`, `IdleHitSnapshot`, `HOME_CANVAS_GRID_TAG`, drag-route/pointer-end predicates in `HomeCanvasGrid.kt`: they are presentation/gesture, owned by later slices.
- Stopped after 8.1: the remaining slices move stateful editor/gesture/overlay code and each needs its own verify cycle.
- Tripwire test not deleted: the decomposition gate is not met.

## Validation (each run separately, 2026-10-06)
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks --console=plain`: PASS. XML summed: `:core:grid` 184, `:feature:home` 547, `:app` 108, 0 failures.
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS (reports written, no failure).
- `./gradlew :feature:home:connectedDebugAndroidTest`: SM-G986W 82 tests, 0 failures (after wakeup + dismiss-keyguard).
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=...ShellForwardingTest,...ShellPickerTest`: 33 tests, 4 failures, the pre-existing set (`MainActivitySeededHomeDeviceTest` ×3 timeouts, `WorkProfileLaunchProofTest` work profile 150 missing from catalogue). Benign `appops set androidx.test.services` error line seen.
- `repo_map.py`, `module_graph.py` (no new edges), `function_audit.py` (`HomeCanvasGrid` 621 lines), `compose_audit.py` (only old comment hits): run.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests, 1 failure, the accepted `HomeCanvasGrid` tripwire (threshold untouched).
- `git diff --check`: clean.

## Unverified / known gaps
Real-finger drag and edge hold; drop followed by a normalization shift; possible one-frame flash when the loader's `StateFlow` update lags the drag-state clear: all not run.

## Worktree
Dirty with uncommitted Phase 3.2–8.1 work on HEAD `cb3d38f`. Nothing staged or committed.

## Next action
Slice 8.2: move `HomeEditState` and its pure transitions into `HomeGridEditor.kt` (no repository calls, minimal Compose). Exit: unit counts unchanged (184/547/108), `:feature:home` device suite 82/0, app suite same 4 failures, no new Gradle edges.
