# 2026-09-28 — A3 unified edit-drag + page-swipe arbitration (MOVE-only)
Status: code-complete; full-device interaction proof outstanding (new instrumented drag tests pass on SM-G986W)
Owner/slice: `feature:home` (+1 `:app` unit test), `docs/agents/tasks/02.md` A3

## Baseline and worktree
Started from a worktree with concurrent changes (feed AIDL work, shortcut ordering,
C1 tile layouts, agent-doc edits). Those were preserved untouched; this slice edited
only the files listed below. `HomeCanvas.kt`, `LauncherShell.kt`, `AppTile.kt`,
resize route, grid, shortcut layout/persistence, feed and nav were not touched.
`git status` at start already showed `M HomeCanvasGrid.kt` (one-line A2 follow-up:
long-press independent of launchability) — kept as-is.

## Completed and decisions
Unified edit-drag per the A3 card (MOVE-only; selected-tile RESIZE branch untouched):

- `feature/home/.../HomeCanvasGrid.kt` idle observer: while `Editing`, ANY
  tile-origin down yields to the drag handler (was: only the selected tile).
  Normal tile/background downs keep the existing axis arbitration; empty-background
  horizontal swipes stay pager-eligible.
- `dragModifier`: any edit-mode tile down (whole tile surface via the geometric
  `downTile` hit, never subregions) is admitted as a MOVE candidate and holds
  `pagerHold` while pending. Below slop the path returns untouched so taps keep
  the child-tap/select route. Past slop a non-selected tile transfers selection
  via `selectOnTap` (clears the resize arm) then enters `HomeEditSession` MOVE;
  the slop consume blocks child launch/shortcut callbacks. Transfer drags route
  direct MOVE with no resize interpretation (`transferDragRoute`); the
  selected-tile armed snapshot + `routeDownDrag` RESIZE/NONE logic is unchanged.
  Grab offsets anchor on the down tile (identical to selected bounds for
  selected-tile drags).
- New pure helpers (bottom of file, MOVE-only): `isEditTransferDrag`,
  `transferDragRoute` — single source used by the drag handler and unit-tested.
- Step-3 hold handling needed no new code: the broadened pending flag flows
  through the existing pre-slop exits (pending-gate, slop-wait release) and the
  MOVE release/cancel path already clears `pagerHold` on release AND cancel with
  no commit on cancel (`moveCancelled` → commit null). Verified by reading the
  MOVE exit block; covered by new tests.
- Tests: new `EditTransferDragTest` +10 (transfer predicate, transfer-always-MOVE
  incl. armed-arrow, selectOnTap→enterSelectedDrag end-to-end, no arm inherit);
  `HomeGestureArbitrationTest` +2 (edit-tile drag holds, background stays free);
  `LauncherPagerGateTest` +1 (`pagerHold` blocks/restores); `HomeCanvasTest` +4
  real-pointer instrumented (unselected app-origin drag → one `onMoveTile(B)` +
  selection to B + no launch/shortcut/resize + hold true→false; shortcut-origin
  drag → whole-tile move + no shortcut fire; below-slop tap selects with no move;
  `onPagerHoldChange` true→false on tile touch). `LauncherShellForwardingTest`
  untouched (shell not changed).

## Validation (each in its own shell invocation)
- `./gradlew :feature:home:testDebugUnitTest` — SUCCESS, 587 tests / 32 classes,
  0 failures, 0 errors (incl. 10 new + 2 new arbitration).
- `./gradlew :app:testDebugUnitTest` — SUCCESS (LauncherPagerGateTest 17/17 incl.
  new test). NOTE: first attempt failed in `:platform:feed:compileDebugKotlin`
  (unresolved `AndroidFeedPreflight`, `FeedProviderTarget` mismatches, etc.) from
  concurrent feed work in the shared worktree — unrelated to this slice, no feed
  files touched; rerun passed with no changes on my side.
- `./gradlew :feature:home:assembleDebug` — SUCCESS.
- `./gradlew :feature:home:lintDebug` — SUCCESS.
- `./gradlew :feature:home:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...HomeCanvasTest`
  on SM-G986W — 45 tests, 41 pass incl. all 4 new A3 tests; 4 failures are
  pre-existing in untouched areas: `removeAffordance_hiddenBeforeEdit`
  (test-main-thread `fetchSemanticsNodes`-in-`runOnIdle` violation),
  `physicalTap_shortcut_reportsOwnIdOnly_noLaunch` (post-failure harness cascade),
  `removeAffordance_smallTile_topRightCorner` (180 vs 144px device-density
  expectation), `publicSignatures_...` (on-device reflection of HomeCanvas entry).
  First full-suite attempt additionally timed out with wider cascade; filtered
  re-run isolates the above 4. (First drag-test version used in-node `swipeDown`
  and committed nothing — within-tile swipes never leave the origin cell; fixed
  with grid-local `swipe(start,end,500)` spanning 600px.)
- `./gradlew installDebug` — SUCCESS, installed `app-debug.apk` (+ fixtures) on
  SM-G986W.
- `python3 tools/scripts/repo_map.py`, `python3 tools/scripts/module_graph.py` —
  ran; no new modules/deps (`feature:home` → core only, unchanged).
- NOT run: `function_audit.py` / `compose_audit.py` (read-only audits; no
  boundary change — single-file MOVE-only diff plus tests — but orchestrator may
  run them at the E gate).

## Remaining and next action
Device-interaction proof outstanding: on SM-G986W enter edit via long-press on
tile A, drag unselected tile B from app symbol / shortcut glyph / empty body past
slop and verify B moves as one unit, selection transfers to B, no launch/shortcut
fires, tile-origin drag never pages, hold clears (no stuck pager), normal
background swipe still pages, and below-slop tap on B selects without moving.
Next action: orchestrator runs that device pass (or delegates a bounded
gesture-verification slice); exit when each behavior is confirmed or a concrete
failure is filed as a follow-up. Phase-B resize work must keep MOVE-only diffs
separate (shared `dragModifier` file — one writer at a time).
