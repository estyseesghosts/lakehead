# 2026-10-06 — Home grid repair Phase 4: replace gesture routing (slices 4.1–4.4)

Status: implemented; unit-tested; 4.4 integration test run on a real device (SM-G986W, Android 13). Not committed.
Owner/slice: `:feature:home`; spec `docs/agents/tasks/grid-repair-phase4.md`, plan `docs/lakehead-grid-repair-plan.md` Phase 4.

## Baseline and worktree
HEAD `cb3d38f`. The worktree already held uncommitted Phase 3 Slice 3.2 work (core/grid `GridRules`, `HomeEditState`, eight test files); it was left intact and this work sits on top of it. **Nothing staged, nothing committed**; no reset/stash/clean.

Baseline claims from the Phase 3 handoff were re-checked by the forced run below: 569 `:feature:home` tests (now 593 with this phase's 24 additions) and 184 `:core:grid`.

## Completed work
- **4.1** `HomeGridGestures.kt` (new). `homeDragGesture(host, handleSizePx)` is the body of `awaitEachGesture`: `awaitDownTile` (hit test + grab offset + arm snapshot) → `awaitSlop` → `chooseRouteOnce` → `beginDrag` → `trackDrag` → `finish()` → one `host.onDragEnded(route, HomeDragEnd)`. The two near-duplicate RESIZE/MOVE branches (slop loop, end classification, steal check, commit, pager reset, lease clearing) collapsed into one `trackDrag` and one pure `resolveDragEnd` (`Commit(HomeEditCommit)` or `Discarded(CANCELLED|STOLEN|ABORTED|NO_CHANGE)`). `HomeCanvasGrid()` went 1,420 → 957 function lines; file 2,294 → 1,831.
- **4.2 / 4.3** `HomeDragRoutingTest` (20 tests): full MOVE matrix and RESIZE position-invariance matrix, plus `resolveDragEnd` cases. Row < 0 is asserted on the raw `RequestedCell`/`MoveDragMath.cellForPixel` boundary.
- **4.4** `HomePagerGate.kt` (new): single owner of `hold` and `axisLock`. The idle loop moved out of the composable into `homePagerGesture`. `HomePagerGateTest` (4) and `HomeDragPagerArbitrationTest` (4, instrumentation).

## Gate status (honest)
- **4.1: partly delivered.** The loop reads top-to-bottom and emits a named commit intent. The gate also says "without following persistence … structural re-freeze callbacks". That part is **deferred**: the acknowledgement lease/MOVE frontier still run behind `HomeDragHost.onDragBegan`/`onDragEnded` (→ **Phase 7**), and `rebaseMetrics` still runs per event via `HomeDragHost.liveMetrics` (→ **Slice 5.3 / 9.1**). Both are marked temporary in code.
- **4.2 DONE** (unit). **4.3 DONE** (unit). **4.4 DONE** (device).

## Decisions and reasons
- **Host interface instead of lambdas.** `HomeDragHost` is the one seam; the composable implements it over its Compose state. Reason: the loop needs ~10 reads/writes; one named interface keeps them reviewable and lets legacy members be deleted individually.
- **Route chosen once from the down snapshot.** `inHandle`/`armedAtDown` are computed at down on the dragged tile's bounds (same inputs the old code used at slop), so behavior is unchanged but the route can no longer be re-derived mid-gesture.
- **Axis lock removed from steal detection (behavior change).** The drag loop used to cancel when `pagerAxisLock` changed. The idle observer is not installed during a drag and the hold is claimed, so this path was effectively dead; it also contradicted "pager state must not alter edit state". Only a foreign vertical scroll now steals. Pager hold is still claimed for the drag's lifetime.
- **Fresh state (behavior change).** The old drag lambda read `loaded.tiles`, `onMoveTile`, `onResizeTile` from closures captured when the pointer scope (keyed on home id + visibility) last restarted. The host now reads `idleHitInputs`/`rememberUpdatedState`. This fixes stale occupancy snapshots for a second drag in the same scope; noted rather than separately tested.
- `shouldDiscardFrontierOnMoveEnd` is now unreferenced from production code (still unit-tested); delete with the frontier in Phase 7/9.

## Touched paths
New: `feature/home/src/main/.../HomeGridGestures.kt`, `HomePagerGate.kt`; tests `HomeDragRoutingTest.kt`, `HomePagerGateTest.kt`; `feature/home/src/androidTest/.../HomeDragPagerArbitrationTest.kt`; `docs/agents/tasks/grid-repair-phase4.md`.
Modified: `HomeCanvasGrid.kt` (drag/idle modifiers replaced, `IdleHitSnapshot` made `internal`, pager vars → `HomePagerGate`), `state-logs/CURRENT.md`.

## Validation (exact)
- `./gradlew :feature:home:compileDebugKotlin --console=plain -q` PASS.
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest --rerun-tasks --console=plain` BUILD SUCCESSFUL, 50 tasks executed (forced). From result XML: `:feature:home` 593 tests / 0 skipped / 0 failures / 0 errors; `:core:grid` 184 / 0 / 0 / 0.
- `./gradlew assembleDebug --console=plain -q` PASS. `./gradlew lint --console=plain -q` PASS (reports written, no errors).
- `repo_map.py` ran; `module_graph.py` ran — graph **unchanged**; `function_audit.py` 17 findings, `HomeCanvasGrid()` 957 lines; `compose_audit.py` 3 pre-existing comment/string matches; `git diff --check` clean.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 run, **1 FAIL** — the known tripwire (`957 not greater than 1500`). Threshold not touched; remains accepted baseline debt until Phase 8.
- Device (SM-G986W): `./gradlew :feature:home:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…HomeDragPagerArbitrationTest` → 4 tests, 0 failures (result XML). The first run failed 3/4 because the test grabbed the exact tile centre, which lies inside the shrunk resize-handle zone and correctly routes an unarmed drag to NONE; the test now grabs the upper-left quadrant. The `backgroundSwipe_stillNavigatesThePager` control proves the harness can observe navigation.
- Device: `…HomeCanvasTest` (existing, full class) → everything passed except `removeAffordance_smallTile_topRightCorner_tapCallsOnRemoveOnly` (`expected:<180.0> but was:<144.0>`, remove-button geometry). It touches no gesture code and `HomeCanvasTest.kt` is unmodified, but it was **not** compared against HEAD (that needs a checkout/stash I did not do), so "pre-existing" is unverified.

## Unverified device behavior
Only the 4.4 harness (synthetic `performTouchInput` swipes inside a Compose `HorizontalPager`) and the existing `HomeCanvasTest` were run. Not verified: a real finger on the real `LauncherShell` pager; resize-handle drags on device; steal/hide behavior; the fresh-state change across consecutive drags; no other instrumentation module (`app`, `designsystem`, `platform`) was run.

## Known failures and incomplete work
- Tripwire test red (see above). `HomeCanvasGrid.kt` is still the largest function in the repo (957 lines).
- `HomeCanvasTest.removeAffordance_smallTile_topRightCorner…` red on device, cause/age unknown.
- Retired machinery intentionally left: lease/frontier, `rebaseMetrics`, `originAnim`, edge ticker (Phases 5–7/9).
- Noticed, not fixed: the drag-routing zone shrink makes the centre of a ~half-handle-sized tile a handle hit; routing is correct but a user cannot drag an unarmed tile by its centre if the tile is ≤ ~2× the handle size.

## Next action
Run `./gradlew :app:connectedDebugAndroidTest` (or at minimum `LauncherShellForwardingTest`) on the SM-G986W to confirm the shell's pager hold still gates swipe with the new `HomePagerGate`; exit condition: the run is green or its failures are classified as pre-existing by reading the same tests at HEAD. Then Phase 5 Slice 5.1 (`HomeGridLayout`) per the plan.
