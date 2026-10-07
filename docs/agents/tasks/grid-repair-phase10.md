# Home grid repair Phase 10 — test-suite rebuild around contracts

Authority: `../../lakehead-grid-repair-plan.md` §3 (1–15), §5 Phase 10 (slices 10.1–10.6, exit gate), §9 non-goals.

Status: **scoped (2026-10-07), not started**. Step Zero device confirmation is done
(see §0). No test has been changed, renamed, or deleted under this spec yet.
Phase 9 evidence: `grid-repair-phase9.md` ("Kept" list) and
`../../../state-logs/handoffs/2026-10-07-grid-repair-phase9.md`.

## Ownership and rules

`:feature:home`, `:core:grid`, `:core:designsystem`, `:app` (tests only, plus
the minimal production renames Phase 10 names). Behavior unchanged.
`GridRules` and `GridRowGapNormalizer` stay off limits unless a Phase 10 slice
names them. No new Gradle modules or edges. Do not re-add any lease, ack, or
request-id concept. Do not start Message Center, search, widgets, or All Apps
grouping.

Test discipline (plan §5 Phase 10 exit gate): tests exercise behavior and
invariants — edges, collisions, holes, spans, deterministic packing, width
adaptation — not the deleted patch machinery. Most correctness coverage runs
without Compose. A test is deleted only when it exclusively exercises a deleted
symbol or duplicates a surviving test; record each by name and count and name
the covering test.

Baselines at scope time (HEAD `c2613b7`, clean tree): unit `:core:grid` 130,
`:core:designsystem` 155, `:feature:home` 537, `:app` 108, 0 failures
(@Test counts summed per file, verified 2026-10-07). Device baselines in §0.

## §0 Step Zero — Phase 9 device confirmation (DONE 2026-10-07)

- Wake: `adb -s R5CN70E2SGV shell input keyevent KEYCODE_WAKEUP`, then
  `adb -s R5CN70E2SGV shell wm dismiss-keyguard` (appops error line benign).
- `./gradlew :feature:home:connectedDebugAndroidTest --console=plain` →
  82 tests, 0 failures on SM-G986W. Matches baseline. No Phase 9 regression.
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=me.foxtails.lakehead.LauncherShellForwardingTest,me.foxtails.lakehead.LauncherShellPickerTest --console=plain` →
  33 tests, exactly the 4 known failures, no other failure:
  * `MainActivitySeededHomeDeviceTest.sparsePlacements…` — ComposeTimeout at
    `waitForText` (`MainActivitySeededHomeDeviceTest.kt:247` via `:179`).
  * `MainActivitySeededHomeDeviceTest.explicitlySeededEmptyHome…` — same
    ComposeTimeout shape (via `:82`).
  * `MainActivitySeededHomeDeviceTest.seededPlacements…` — same shape (via `:132`).
  * `WorkProfileLaunchProofTest.sharedComponent…` — AssertionError
    `resolveCatalogueProfiles` (`WorkProfileLaunchProofTest.kt:174`):
    accessible non-personal `(UserHandle{150}, ProfileId(150))` not represented
    in catalogue `ProfileIds=[0]`.
- The 4 failures are the same 4 test names as the recorded baseline
  (`MainActivitySeededHomeDeviceTest` ×3, `WorkProfileLaunchProofTest`); the
  failure signatures are environment-shaped (seeded-DB/role overlay and
  managed-profile availability), not grid-math regressions. Investigate before
  rewriting anything (slice 10.6d).
- 19 designsystem androidTests (`WindowedSpanContainerTest`) were deleted on
  purpose in Phase 9 and must not be re-added.

## Audited scope (before any rebuild)

### 10.1 Geometry tests — cell → rect → cell, spans, widths, rest, scroll

Goal: plan §5 10.1. One immutable metrics object owns every tile rectangle and
the inverse top-left mapping; leading rows are real; one row-stride of scroll
moves the request exactly one row.

| File (unit @Test) | Covers | Invariant it must guard |
|---|---|---|
| `feature/home HomeGridGeometryTest` (8) | `homeGridMetricsFor`, `tileAt`, content/origin helpers | logical top-left → pixel → logical top-left round trip; nonzero rest offset folds into both directions |
| `core/grid GridVerticalLayoutTest` (10) | `GridVerticalLayout.measure` absolute extent | content bottom = `max(row+span)` from row 0; leading rows preserved; empty = 0 (see 10.1a) |
| `feature/home ContentMinHeightPxTest` (7) | `contentMinHeightPx` row-count height | height counts cells+gaps, saturates on overflow, never rebases `minRow` |
| `feature/home HomeCanvasSignedRendererTest` (7) → rename `HomeCanvasRestOffsetTest` | `contentMinHeightPx`, `signedRestFor` (→`restFor`), `signedFrame` (→`pixelFrame`) | tall content anchors row 0 at zero; short content applies 0.45 bias; frame top = `rest + row*stride` for every span (absolute-row, §10.1b) |
| `core/grid GridRestOriginTest` (8) | `GridRestOrigin.rest(minRow=0)` | rest is presentation-only; `minRow` is permanently 0 |
| `core/grid GridViewportAdaptationTest` (10) | column-count adaptation | 6 square 1×1 cells across on a normal phone; wider viewports vary columns without changing persistence |
| `core/designsystem SquareCellGeometryTest` (22), `SixCellGeometryTest` (18) | cell sizing primitives | square cells; every column; representative rows 0/1/5/50; all supported spans 1×1, 2×1, 1×2, 2×2, 4×2 |
| `feature/home HomeNormalViewportTest` (23) | normal-mode scroll range incl. `signedRestFor` cases | scroll range = truthful absolute content; rest offset never moves legality |
| `feature/home HomeViewportTest` (41) | viewport helpers, `viewportRowsFor`, collapse | viewport math floors division, clamps, never invents legality |
| `feature/home HomeCommittedScrollTest` (7) | committed extent/scroll | committed content bottom drives scroll; workspace growth does not alter candidates (bridge to 10.4) |
| `feature/home CenteredOriginXTest` (4) | horizontal centering | X origin centers short grids, never shifts columns |

Known 10.1a `measure` cleanup (audited, not yet changed):
`GridVerticalLayout.kt:17-21` — `fun <T : Any> measure(placements, viewportRows,
workingMarginRows)`. `T` is dead (only `List<T>` position; every caller passes
`List<GridPlacement<…>>`; the `else -> error` at `:35` is unreached — no test
hits `unsupported placement type`). `viewportRows` (`:22`) and
`workingMarginRows` (`:23`) are validated but never touch the math (`:25-56`,
absolute row-0). 23 direct call sites, all passing `workingMarginRows`:
production 3 (`HomeGridGeometry.kt:471,528` literals `0L`;
`HomeRenderProjection.kt:59` passthrough whose only production input
`MainActivity.kt:147-153` defaults `0L`), tests 20
(`GridVerticalLayoutTest` 15, `HomeCommittedScrollTest` 2,
`HomeRenderProjectionTest` 2 direct + 12 via `measureExtent`,
`HomeAbsoluteLayoutTest:60` 1). Slice: collapse the generic to
`List<GridPlacement<*>>`, drop the dead branch, then decide the two
validate-only args (delete or keep as a documented call boundary) and update
the ~30 vestigial call sites in one bounded edit. `GridRules`/
`GridRowGapNormalizer` untouched.

Known 10.1b signed-name cleanup (audited, not yet changed):
`HomeGridGeometry.kt:82 signedFrame` is `rect().roundToInt()` — no signed logic.
`HomeGridGeometry.kt:218 signedRestFor` documents `minRow = 0` itself and
delegates to `GridRestOrigin.rest`. `SignedPixelFrame`
(`core/designsystem TileBounds.kt:7`) carries no signed origin. Slice: rename
`sign\
edFrame → pixelFrame` (callers: `HomeGridLayout.kt:47`,
`HomeGridOverlay.kt:85`, `HomeGridGestures.kt:127,131`,
`EditShortcutMenu.kt:107`, `CandidateOverlays.kt:34,51`),
`signedRestFor → restFor` (caller `committedFrameFor` via `:501`), and
`HomeCanvasSignedRendererTest → HomeCanvasRestOffsetTest` with
`signed*_` test names. Pure rename; behavior unchanged.

### 10.2 MOVE tests — absolute mapping, invalid stays invalid, span fixed

Goal: plan §5 10.2. Current pointer + current scroll − immutable grab offset →
requested cell; out-of-bounds stays invalid with last-valid preview; cancel
restores committed placement; span byte-identical across every MOVE test.

| File (unit @Test) | Covers | Invariant it must guard |
|---|---|---|
| `feature/home MoveDragMathTest` (15) | `MoveDragMath.cellForPixel` absolute mapping | pointer+scroll−grab → raw cell; above-row-0 negative stays invalid; right-edge overflow never clamps |
| `feature/home HomeEditStateTest` (29) | `beginMove/dragTo/finish/cancel` pure transitions | MOVE cannot mutate span; invalid request retains last-valid preview; cancel restores committed |
| `feature/home MoveResizeRoutingTest` (21) | move/resize route separation | position-after == position-before for resize paths; span-after == span-before for move paths |
| `feature/home MoveResizeRegressionTest` (8) | past hardening cases | one cell left/right/up/down, diagonal, 1×1 and 4×2, center/corner grabs, collision |
| `feature/home HomeDragRoutingTest` (20) | route fixing + `resolveDragEnd` | route chosen once at slop; only a visible lift commits (see 10.2a) |
| `feature/home HomePointerLoopRegressionTest` (25) | pointer-loop regressions | stationary pointer + one row-stride scroll ⇒ requested row +1 exactly |
| `feature/home EditTransferDragTest` (13) | transfer/slop drag path | transfer preserves grab; slop does not recenter large-tile corner grabs |
| `feature/home HomeHoldTest` (13) | `isHomeHold/gridHoldSignal` | edit (incl. any drag) holds the pager; empty grid never holds |
| `feature/home HomeGestureArbitrationTest` (31) | gesture arbitration pure rules | locked axis, slop ownership, no mid-drag mode change |

Known 10.2a STOLEN (audited, not yet changed): `DragExit.STOLEN`
(`HomeGridGestures.kt:45`) and `HomeDragDiscard.STOLEN` (`:36`) have no
production producer — `trackDrag` (`:202-224`) returns only
CANCELLED/LIFTED/STALE, with `Pager state is never consulted` (`:212`). Only
coverage is `HomeDragRoutingTest:309-311`, a pure `resolveDragEnd` unit that
synthesizes STOLEN (not a pager gesture proof). `releaseCommit`
(`HomeGridEditor.kt:353`) takes `stolen: Boolean` solely from
`exit == DragExit.STOLEN` (`HomeGridGestures.kt:56`). Slice: either delete both
STOLEN variants + the `:309-311` assertion (if pager-steal is retired) or keep
and rename to `PAGER_TAKEN` with a real pager-arbitration producer + device
proof (`HomeDragPagerArbitrationTest`). Do not do both; decide in the slice.

### 10.3 RESIZE tests — position fixed, spans reachable, handle-only entry

Goal: plan §5 10.3. Resize starts from the immutable tile top-left; pointer
fixes only the requested span through `RenderableSpans`; position unchanged on
every path incl. invalid and cancel.

| File (unit @Test) | Covers | Invariant it must guard |
|---|---|---|
| `feature/home ResizeDragMathTest` (30) | resize span math | each reachable span transition; collision/right/top constraints; invalid keeps preview/original per contract |
| `feature/home ResizePreviewSpanTest` (5) | preview span | preview span never moves position |
| `feature/home IsInHandleTest` (15) | handle hit test | resize starts only from the armed handle path |
| `feature/home CornerControlContractTest` (8), `CornerControlOffsetTest` (4) | handle geometry | handle bounds derive from metrics rect; grabbing handle cannot toggle mode off pre-route |
| `feature/home EditControlPlacementTest` (11) | control placement | selection outline/unpin/handle derive from `metrics.rect()` |
| `core/designsystem TileBoundsTest` (10), `TileHitTargetTest` (6), `FloatingOverlayFrameTest` (8), `SafeControlFloorTest` (8) | bounds/hit primitives | grid rect, hit-test rect, overlay rect agree for the same tile |

### 10.4 Workspace / auto-scroll tests — extent only, deterministic remap

Goal: plan §5 10.4. `workspaceBottomRow` controls measured scroll height only;
growth never alters legality; downward edge scroll extends real content first;
upward stops at logical top; stationary pointer + scroll remaps deterministically.

| File (unit @Test) | Covers | Invariant it must guard |
|---|---|---|
| `feature/home HomeEditWorkspaceTest` (7) | transient edit extent | candidate at row 30 identical before/after workspace growth; growth changes scroll extent only |
| `feature/home HomeEditFrontierTest` (24) → rename/split to `HomeAutoScrollTest` | auto-scroll zones/intervals + unrelated remap/commit cases (see 10.4a) | top/bottom bands scroll by one stride; dwell + depth-scaled interval; middle/zero-height never scrolls |
| `feature/home HomeCanvasOriginAnimTest` (8) | rest-offset animation freeze | entering a drag cannot move the coordinate frame under a stationary pointer |
| `feature/home HomeGridLayoutTest` (7) | absolute placement | tile at row N renders from row N regardless of other rows |
| `feature/home HomeRenderProjectionTest` (27) | projection incl. `measureExtent` passthrough | projection preserves absolute rows; extent parity remeasure paths |

Known 10.4a frontier-test rename (audited, not yet changed):
production `HomeAutoScroll.kt:12` holds only timing constants + zone/step/interval
helpers; the test class `HomeEditFrontierTest.kt:23` still names the retired
`HomeEditFrontier` legality machine and mixes ~5 subjects (zone classification,
step/interval, `viewportRowsFor`, `MoveDragMath` stationary remap, release
commit/`preserveAnchorOnResize`/`clampViewportOffset`). Slice: rename to
`HomeAutoScrollTest` for the zone/interval cases and relocate the remap/commit
cases to the files that own them (or a `HomeMoveRemapTest`); drop the unused
`GridVerticalLayout` import (`HomeEditFrontierTest.kt:5`).

### 10.5 Repository normalization tests — accepted set authoritative

Goal: plan §5 10.5. Normalization runs post-commit only, never during pointer
updates; the accepted normalized set reconciles UI state; leading rows survive;
>2-row gaps normalize per standing policy.

| File (@Test) | Covers | Invariant it must guard |
|---|---|---|
| `core/grid GridRulesTest` (38) | bounds + collision only | column bounds, row ≥ 0, positive span, rectangle intersection; workspace size never affects legality |
| `core/grid GridRowGapNormalizerTest` (9) | post-mutation policy | leading empties untouched; >2-row gaps collapse deterministically; requested drop vs accepted final distinguished |
| `core/grid GridFirstFreeCellTest` (13) | deterministic packing | first-free-cell order is deterministic over holes/spans |
| `app HomePlacementReconciliationTest` (7) | reconcile-from-accepted | UI state projects from `MutationResult.Accepted`, not from the request alone |
| `app HomeRouteLoaderTest` (26) | loader projection | accepted placements → render tiles; failure restores prior state |
| `data/repository RoomHomePlacementRepositoryTest`, `ShortcutBindingRepositoryTest` | mutation + bindings | persist + reload round trip; normalization authoritative after write |
| `core/grid ShortcutStripGeometryTest` (42), `feature/home TilePinnedStripTest` (12), `ShortcutOrderingTest` (37), `TileShortcutPolicyTest` (6), `SwipeSurface*` (11+5), `EditShortcutMenuTest` (35), `HomeCanvasTilePinnedTest` (9) | shortcut/strip surfaces | ordering/pinning policy stable under grid rebuild (grouped, not per-slice) |

`GridRules` / `GridRowGapNormalizer` production stays untouched; only their
tests get rebuilt around the contract above.

### 10.6 Small Compose integration suite — high-value end-to-end only

Goal: plan §5 10.6 (11 cases). Move implementation-detail cases out of the
giant `HomeCanvasTest` into the pure suites above.

| File (device @Test) | Covers | Invariant it must guard |
|---|---|---|
| `feature/home HomeCanvasTest` (47) | giant integration legacy | trim to: horizontal / vertical / diagonal move, collision rejection, top+right rejection, large-tile corner grab without recentering, handle resize, downward auto-scroll while dragging, gesture cancel, row-5 leading-rows retention |
| `feature/home HomeAbsoluteLayoutTest` (9) | absolute layout on device | tiles land on absolute rows on real Compose measurement |
| `feature/home HomeRestOffsetTest` (5) | rest offset on device | short-grid centering is presentation-only and frozen during edit |
| `feature/home HomeDragPagerArbitrationTest` (4) | pager-vs-grid ownership | active tile drag is never stolen by page navigation (pairs with 10.2a decision) |
| `feature/home EditShortcutMenuComposeTest` (17) | shortcut menu compose | menu placement/behavior on device |
| `app MainActivitySeededHomeDeviceTest` (4), `WorkProfileLaunchProofTest` (1), `MainActivityAppLaunchTest` (1), `MainActivityCatalogueLifecycleTest` (1), `MainActivityPackageMutationTest` (1), `SettingsMenuOverlayTest` (8), `FeedSurfaceTest` (16), `LauncherApps*` (4) | shell/catalogue/lifecycle | HOME wiring, catalogue invalidation, package add/remove without restart |

Known 10.6d app-device investigation (before any rewrite): reproduce the §0
four failures, then determine for each whether it is environment (no managed
profile on R5CN70E2SGV; seeded-DB/role-overlay timing) or a Phase 9 product
regression. Only then decide: quarantine as known-environment, fix the cause,
or rebuild the test around the single-route contract. No other `:app` device
failure is acceptable without a fix.

## Explicit non-goals (plan §9)

Leave `FeedOverlayGatewayImpl.kt` for a separate audit. No unrelated page
redesign, no virtualization for hypothetical tile counts, no tile visual
redesign, no repository/database rewrite beyond the ack/row-bound removal
already done, no module-boundary churn to reduce type count.

## Exit gate

- §0 device confirmation recorded (done above); any non-listed device failure
  stops the phase as a regression.
- Slices 10.1–10.6 rebuilt per the file table; every surviving test names the
  invariant it guards.
- 10.1a (`measure` generic/vestigial args), 10.1b (signed names), 10.2a
  (STOLEN), 10.4a (frontier-test rename), 10.6d (app-device investigation) each
  closed with a named decision and covering test.
- Most correctness coverage runs without Compose; the Compose suite holds only
  the §10.6 high-value cases.
- Final validation: `assembleDebug`, `lint`, both device suites, `repo_map.py`,
  `module_graph.py`, `function_audit.py`, `compose_audit.py`,
  `python3 -m unittest discover -s tools/scripts -t tools/scripts` (15 tests),
  `git diff --check` — each run one command at a time.

## Next action

Write `state-logs/handoffs/<date>-grid-repair-phase10.md` entry with this scope
as the baseline and repoint `CURRENT.md` at it; exit when the handoff exists and
CURRENT points at it. Then take slice 10.1a as the first bounded edit.
