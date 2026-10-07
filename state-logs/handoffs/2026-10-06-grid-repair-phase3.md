# 2026-10-06 — Home grid repair Phase 3: authoritative `HomeEditState` (slices 3.1–3.4)
Status: unit-complete for 3.1–3.4; Phase 3 not device-verified.
Owner/slice: `:feature:home`; spec `docs/lakehead-grid-repair-plan.md` Phase 3 (task: `docs/agents/tasks/grid-repair-phase0.md`).

## Baseline and worktree
HEAD `cb3d38f` (slices 3.1/3.3/3.4). Slice 3.2 is the only uncommitted work; worktree dirty, **nothing staged, nothing committed**. `:core:grid` is now touched (see 3.2); `:core:model` is not.

Verified at session start (read from code, not from the plan):
- `HomeCanvasGrid.kt` was 2,744 lines; the `HomeCanvasGrid()` composable spanned lines 177–1897 (1,721 lines). Now 2,294 file lines / 1,420 function lines.
- Edit state had been split across `HomeEditSession` (Idle/Active, mixed position+span+mode), `HomeInteractionController` (Normal/Editing(selectedId, resizeArmed)), a Compose `editSession` var, `LiveEditSessionRef.session`/`.moveFrame` (synchronous mirror) and `editFrontier`/`pendingMove` (legacy legality/lease, Phase 7/9).
- All users of these types are inside `:feature:home`.

## Slice exit conditions (from the plan) and status
- **3.1 DONE** — MOVE cannot mutate span, RESIZE cannot mutate position; one `HomeEditState` owns selection, arm and drag.
- **3.2 DONE** — candidate update has no O(tile-count) DTO conversion/allocation beyond rectangle collision against the stable occupancy collection. See decisions; this required a `core:grid` addition, not just a feature change.
- **3.3 DONE** — editor behavior covered by normal Kotlin tests, no Compose runtime.
- **3.4 DONE** — one authoritative copy; no "authoritative" comments needed to disambiguate.

## Decisions and reasons
- **3.2 approach: occupancy snapshot in the drag state.** `beginMove`/`beginResize` convert tiles once into `List<GridPlacement<ItemId>>` and store it on `DragState.Move`/`.Resize`. `updateMove`, `moveTo`, `updateResize`, `resizeTo` and `rebaseAndMoveTo` no longer take `placements`. Reason: the previous signature converted the whole loaded tile list on *every pointer event* (O(n) DTO allocation per frame).
  - `GridRules.move`/`resize` already ignore the dragged tile's own footprint via `ignoredIndex`, so the snapshot legitimately *includes* the dragged tile. No `UNKNOWN_ITEM` on the public path.
  - **`core:grid` addition — required to actually meet the exit condition.** Even with the snapshot, `GridRules.move`/`resize` build a full replaced `placements` list on every *accepted* call, and `HomeEditState` discarded it (it only reads validity). That is still an O(n) allocation per update, so the predicate could not be met in `:feature:home` alone. Added `GridRules.moveRejection`/`resizeRejection` returning `Rejection?`, allocation-free. `move`/`resize` now **delegate to them**, so legality has exactly one implementation and the two shapes cannot diverge. Extracted `replaced()` for the list rebuild. `HomeEditState.validityOf` now maps `Rejection?`.
  - **Staleness trade-off (accepted).** If a tile other than the dragged one is removed mid-drag, the snapshot is stale and conservative: it reports a cell occupied when it is now free, so the preview holds last-valid and release commits last-valid. `onTileRemoved` already cancels the drag when the *dragged* tile is removed. Chosen over re-reading `loaded.tiles` per event (the O(n) cost this slice removes).
  - `beginResize` signature changed to take `placements` as 2nd arg (was `metrics` only). All callers updated.
- **Removed dead plumbing.** `HomeCanvasGrid.kt`'s `loadedSnapshot = rememberUpdatedState(loaded)` and its `snapshotLoaded` reader existed only to feed per-update `placements`; both removed. A missed `placements = snapshotLoaded.tiles` argument at the BOTTOM-zone `rebaseAndMoveTo` call was found and removed.
- **Pre-existing lint failure fixed (not caused by this slice).** `lint` failed on `SuspiciousIndentation` at `HomeCanvasGrid.kt:1064` (also present at HEAD `cb3d38f` — verified by inspecting `git show HEAD:...`). Two blocks had 1-space indentation inside a 20-space block: the `val slopChange` region (1054–1057) and the `LaunchedEffect(moveTickItemId)` comment (602–605). Re-indented to match. Whitespace only, no behavior change.

## Test contract changes worth knowing
- `HomeEditStateTest.updateMove_unknownItem_dropsDrag` previously passed a placements list that *omitted* the dragged tile to force `UNKNOWN_ITEM`. That is now unreachable through the public API (a drag always begins with a snapshot containing itself). Rewritten to construct a `DragState.Move` with an orphaned snapshot directly, asserting the guard still drops the drag and keeps selection/edit mode; a companion test asserts the public path's snapshot always contains the dragged item. Added a `placementsOf` helper plus `GridPlacement`/`GridPosition`/`GridSpan` imports.
- `GridRulesTest`: added `rejectionPredicatesAgreeWithMoveAndResizeOnEveryOutcome` (compares `reasonOf(move/resize)` against the predicates across own-footprint, collision, edge, out-of-bounds, unbounded-row, unknown-id cases) and `rejectionPredicatesRejectNonPositiveColumns`; added a `reasonOf` helper.
  - Note: an initial version of that test asserted `GridPosition(-1, 0)` as an out-of-bounds case. `GridPosition` rejects negative columns at construction, so that input is not expressible; the case was removed (negative coordinates are covered by existing `GridPosition`/`signedBounds` tests).
- Test helpers whose `placements`/`tiles` parameters became unused were given up the parameter rather than left dead: `MoveResizeRegressionTest.moveRequest`, `HomeEditFrontierTest.remap`.
- `:feature:home` now has 569 unit tests; `:core:grid` 184.

## Touched paths
- `core/grid/src/main/java/me/foxtails/lakehead/core/grid/GridRules.kt` — `moveRejection`/`resizeRejection` added; `move`/`resize` delegate; `replaced()` extracted.
- `core/grid/src/test/java/me/foxtails/lakehead/core/grid/GridRulesTest.kt`
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeEditState.kt` — `snapshot` on both drag kinds; signature changes; `validityOf(Rejection?)`.
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvasGrid.kt` — call-site updates, dead `loadedSnapshot` removal, two indentation fixes.
- Tests: `HomeEditStateTest`, `ResizePreviewSpanTest`, `HomePointerLoopRegressionTest`, `EditTransferDragTest`, `MoveResizeRegressionTest`, `HomeEditFrontierTest`, `HomeHoldTest`, `EditShortcutMenuTest`.

## Validation (exact, this session)
- `./gradlew :feature:home:compileDebugUnitTestKotlin --console=plain -q` PASS.
- `./gradlew :feature:home:testDebugUnitTest --rerun-tasks --console=plain -q` PASS — 569 tests, 0 skipped, 0 failures, 0 errors (from result XML, not NO-SOURCE).
- `./gradlew :core:grid:test --console=plain -q` PASS — 184 tests, 0 skipped, 0 failures, 0 errors.
- `./gradlew assembleDebug --console=plain -q` PASS.
- `./gradlew test --rerun-tasks --console=plain` BUILD SUCCESSFUL (Robolectric emits `restricted method`/native-access WARNINGs; these are pre-existing and not failures).
- `./gradlew lint --console=plain -q` PASS (0 errors) after the indentation fix.
- `python3 tools/scripts/repo_map.py` ran (unchanged module/file inventory).
- `python3 tools/scripts/module_graph.py` ran — graph **unchanged**; `:feature:home -> :core:grid` and `:data:repository -> :core:grid` only, no new edge.
- `python3 tools/scripts/function_audit.py` ran — 19 findings; `HomeCanvasGrid.kt` now 1,420 function lines (was 1,721).
- `python3 tools/scripts/compose_audit.py` ran — 3 findings, all pre-existing comment/`LauncherApps` string matches in `AppSymbol.kt` and `HomeCanvasTest.kt`.
- `git diff --check` clean (exit 0).
- `python3 -m unittest discover -s tools/scripts -t tools/scripts` — **1 FAILURE, pre-existing.** `test_function_audit.HomeCanvasGridTest.test_home_canvas_grid_tripwire_size` asserts the `HomeCanvasGrid` body is `>1500` lines; HEAD `cb3d38f` measured **1429**, so it already failed before this slice (verified by running `find_functions` over `git show HEAD:...`). This slice lowered it to 1420. The test's own docstring says Phase 8 decomposition should delete it and that the bound must not be lowered silently. **Left unmodified pending a decision** — see below.

## Unverified device behavior
Nothing was run on a device. No instrumentation test was executed. HOME selection, profile launches and callback delivery remain unproven by this slice. Specifically unverified: that a real drag's per-frame validity still matches what the pre-change implementation produced, and that mid-drag tile removal behaves as the staleness trade-off describes.

## Known failures and incomplete work
- The tripwire test above is red at HEAD and still red. It is now 80 lines away from its own threshold, so the "Phase 8 will delete it" assumption is close to self-fulfilling but the failing test still blocks a clean audit run.
- `HomeCanvasGrid.kt` remains 2,294 lines / 1,420 function lines: the single largest function in the repository, still flagged by `function_audit.py`. Phase 8 decomposition is the planned remedy, not this phase.
- Animated rest offset (`originAnim`) still moves under a drag; MOVE re-freeze remains the minimal `rebaseMetrics` bridge (delete in Slice 5.3/9.1).
- Retired designsystem helpers (`ResizeCandidateOverlay`, `resizeCandidateFrame`, `TileBounds.signedBounds`, `WindowedSpanContainer.rowOrigin`) remain unused by `:feature:home`, awaiting Phase 5/9.

## Worktree/commit state
Nothing staged, nothing committed, no `git add`, no reset/stash/clean. HEAD remains `cb3d38f`. All Slice 3.2 edits are unstaged in the worktree.

## Next action (one, with exit condition)
Decide the fate of `test_function_audit.HomeCanvasGridTest.test_home_canvas_grid_tripwire_size`: either delete it as its docstring instructs now that Phase 3 has reduced the body to 1420 (and record the deletion), or leave it red and log the failure as an accepted baseline debt. Exit condition: `python3 -m unittest discover -s tools/scripts -t tools/scripts` reports 16 passing tests, or a handoff line explicitly accepts the single red test. Do not lower the 1500 threshold silently. After that, Phase 3 is unit-complete and the next slice is Phase 4 per `docs/lakehead-grid-repair-plan.md`.
