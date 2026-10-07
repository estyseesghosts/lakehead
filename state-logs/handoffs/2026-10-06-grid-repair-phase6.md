# Handoff — Home grid repair Phase 6 (2026-10-06)

## Completed (factual)
Slices 6.1–6.4 of `docs/lakehead-grid-repair-plan.md` §5 Phase 6, spec in `docs/agents/tasks/grid-repair-phase6.md`.
- One transient `workspaceBottomRow` (`HomeEditWorkspace.kt`) sizes only measured content height during an active MOVE; the real `ScrollState` follows. Candidates/legality never read it.
- New `HomeEdgeAutoScroll.kt` (`nextAutoScrollTarget`, `runMoveEdgeAutoScroll`): dwell, extend workspace before each downward step, `scrollTo`, remap from the unchanged pointer. No budget, baseline or frame copy.
- Removed the B3 frontier, scroll baseline, steal check, eight-change budget and old ticker.

## Touched paths
Added: `feature/home/.../HomeEditWorkspace.kt`, `HomeEdgeAutoScroll.kt`, `feature/home/src/test/.../HomeEditWorkspaceTest.kt`, `docs/agents/tasks/grid-repair-phase6.md`.
Edited: `HomeCanvasGrid.kt`, `HomeEditFrontier.kt` (reduced to constants, lease helpers, `AutoScrollZone` helpers), `HomeGridGestures.kt`, `HomeCanvas.kt` (comment), `HomeEditFrontierTest.kt`, `HomeCommittedScrollTest.kt`, `HomePointerLoopRegressionTest.kt`, `feature/home/src/androidTest/.../HomeAbsoluteLayoutTest.kt` (+2 device tests, comment).

## Decisions
- Workspace state lives in `HomeCanvasGrid`, keyed by the active MOVE; reset when the MOVE ends. Initial = committed bottom + one visible area + 4 lookahead rows, capped at 200,000 px (Compose measure limit).
- Lookahead means the measured range already covers each step, so no baseline or frame wait.
- Ack lease (`PendingMoveCommit`, `moveCommitAck`, `moveGateOpen`) kept minimal for Phase 7. `DragExit.STOLEN`, `HomeDragDiscard.STOLEN`, `resolveDragEnd` kept with tests but have no production producer now.
- Kept unreferenced `committedScrollMaxPx`/`clampScrollToCommitted` for Phase 9. `HomeEditFrontier` object name retained (constants only); rename is optional cleanup.

## Deleted tests (reasons)
Tests of deleted functions only: init/advance/rebase/editLayoutRows/editContentHeight/editScrollMax and two `tickerBaseline_*` tests (`HomeEditFrontierTest`); `shouldDiscardFrontierOnMoveEnd`, `shouldUseEditLayout`, `contentHeightForLayout` tests (`HomeCommittedScrollTest`); four `shouldInitMoveFrontier` tests (`HomePointerLoopRegressionTest`). `committedMax_ignoresFrontier` was replaced by `committedMax_contentThatFits_isZero`. Net `:feature:home` unit count 587 → 562 (including the new tests).

## Validation (each run separately)
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks`: PASS; `:feature:home` 562, `:core:grid` 184, `:app` 94, 0 failures (result XML summed).
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS (no failure; reports written).
- `module_graph.py`: no new edges. `function_audit.py`: `HomeCanvasGrid()` 696 lines (was 856). `compose_audit.py`, `repo_map.py`: run, only pre-existing comment hits. `git diff --check`: clean.
- `python3 -m unittest discover ...`: 1 failure, the accepted tripwire (696 not > 1500).
- `./gradlew :feature:home:connectedDebugAndroidTest`: SM-G986W 81 tests, 0 failures (79 baseline + 2 new). The second attached device (PLG110) failed to install the test APK and ran 0 tests (environment).
- `./gradlew :app:connectedDebugAndroidTest` (two shell classes excluded): 4 failures, the same pre-existing set (`MainActivitySeededHomeDeviceTest` ×3, `WorkProfileLaunchProofTest`). The run ended with a UTP `NoClassDefFoundError` while writing results, so the total test count was NOT captured. The first attempt of this command was lost (`Shell.NotFoundError`), so this is the second run.

## Unverified / known gaps
- No device test that a scroll step moves the candidate by exactly one row (pure test only).
- The auto-scroll ticker was exercised only through the Compose test clock, not a real finger.
- Rows beyond ~1,400 and the workspace dropping on release (content shrinks before loaded tiles update) not checked on device.
- `docs/lakehead-grid-repair-plan.md` Phase 6 gates were not re-read line by line against the final code.

## Close-out addendum (same day, later session)
Gates re-read against code: 6.1 met (workspace sizes measured height only; row-30 pure test), 6.2 met (`minHeightPx` from the workspace, no synthetic edit max; grep of `feature/home/src/main` for baseline/budget/refreeze/windowFirstRow finds only constants and comments), 6.3 met (no budget, baseline, refreeze or frame mirror), 6.4 met (pure test). No gate unmet.

Added `HomeAbsoluteLayoutTest.oneAutoScrollStep_withAMotionlessPointer_requestsExactlyTheNextRow` (+ `scrollValue()` helper). Single held drag at the bottom edge with the test clock paused: no step before the 200ms dwell; after the first step the scroll offset equals one row stride (180 px on SM-G986W) and the candidate overlay's content-space top (on-screen top + scroll) moved by exactly that stride; nothing committed. A frame is advanced before reading the overlay because the remap is composed a frame after the scroll. The device screen must be awake and unlocked: with it asleep, tests failed with "No compose hierarchies found" (fixed with `adb -s R5CN70E2SGV shell input keyevent KEYCODE_WAKEUP` and `wm dismiss-keyguard`).

Validation (each its own command, all 2026-10-06):
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks --console=plain`: PASS; result XML summed `:core:grid` 184, `:feature:home` 562, `:app` 94, 0 failures/errors.
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS.
- `./gradlew :feature:home:connectedDebugAndroidTest`: SM-G986W 82 tests, 0 failures (the class alone: 9/9).
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=me.foxtails.lakehead.LauncherShellForwardingTest,me.foxtails.lakehead.LauncherShellPickerTest`: 33 tests, 4 failures: `MainActivitySeededHomeDeviceTest` x3 (30s `waitForText` timeouts) and `WorkProfileLaunchProofTest` (work profile 150 missing from the catalogue). Same pre-existing set; total count now captured.
- `repo_map.py`, `module_graph.py` (no new edges), `function_audit.py` (`HomeCanvasGrid()` 696 lines), `compose_audit.py` (only old comment hits): run. `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests, 1 failure, the accepted tripwire (threshold untouched). `git diff --check`: clean.

Still not verified: a real finger holding the edge (not run: a scripted `adb input` drag cannot hold at the edge and release separately, and Home on the device was not set up for a MOVE), rows beyond ~1,400, and the workspace dropping on release against slow loads. `heldBottomEdgeDrag_growsTheScrollRange_withNoCommitUntilRelease` can pass from the initial workspace alone; the new test is what proves a step actually ran.

## Worktree
Dirty with uncommitted Phase 3.2/4/5/6 work on HEAD `cb3d38f`. Nothing staged or committed.

## Next action
Phase 7 Slice 7.1: feature callbacks become `onMoveTile(id, position)`, `onResizeTile(id, span)`, `onRemoveTile(id)`, with no request ids, leases or ack DTOs crossing the boundary. Exit: grep of `feature/home/src` for `PendingMoveCommit|moveCommitAck|moveGateOpen|requestId` is empty and unit and device suites stay at the numbers above.
