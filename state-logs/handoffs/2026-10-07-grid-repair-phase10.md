# 2026-10-07 — Home grid repair Phase 10 (test-suite rebuild) — in progress
Status: in progress
Owner/slice: orchestrator / `docs/agents/tasks/grid-repair-phase10.md`

## Baseline and worktree
- HEAD `c2613b7`, clean tree except untracked scope doc
  `docs/agents/tasks/grid-repair-phase10.md` (written 2026-10-07, no test changed yet).
- Phase 9 complete; unit baselines `:core:grid` 130, `:core:designsystem` 155,
  `:feature:home` 537, `:app` 108 (@Test counts, 0 failures).
- Device R5CN70E2SGV connected (SM-G986W).

## Completed and decisions
- Step Zero device confirmation done 2026-10-07: `:feature:home` 82/0 PASS;
  `:app` (2 shell classes excluded) 33 tests with exactly the 4 known failures
  (`MainActivitySeededHomeDeviceTest` ×3 ComposeTimeout at `waitForText`,
  `WorkProfileLaunchProofTest` managed-profile assertion `UserHandle{150}` vs
  catalogue `[0]`). Same 4 test names as baseline; no Phase 9 regression.
- Phase 10 scope written before any test change: slices 10.1–10.6 file table
  with per-file coverage + guarded invariant, plus named cleanups 10.1a
  (`measure` generic + vestigial args, 23 direct sites), 10.1b (signed names),
  10.2a (STOLEN, no producer), 10.4a (frontier-test rename/split), 10.6d
  (app-device investigation before rewrite).
- Read-only audits completed for 10.1a/10.1b/10.2a/10.4a (grep + reads);
  test-file inventory audit was cancelled and redone by hand via
  `repo_map.py` + `@Test` counts.

## Validation
- `git status --short`: clean except untracked scope doc. `adb devices`:
  R5CN70E2SGV device. Device suites: as above (PASS 82/0; 33/4-known).
- NOT RUN: `assembleDebug`, unit rerun, `lint`, repo/module/function/compose
  audits, `unittest discover`, `git diff --check`, `installDebug`.

## Slice log
- 10.1a-part1 DONE: `GridVerticalLayout.measure` generic collapsed to
  `List<GridPlacement<*>>`, dead `else -> error` removed, explicit type arg at
  `HomeRenderProjectionTest:401` dropped. `:core:grid:test` 130/0,
  `git diff --check` clean. Args kept for part 2.
- 10.6d investigation DONE (`zero`): no Phase 9 regression. `explicitlySeededEmptyHome…`
  and `sparsePlacements…` await the removed HOME-role overlay string
  (`HomePlaceholder` has no production caller; `HomeCanvas.kt:33-35,183-190`
  documents removal) → rebuild tests to current contract. `seededPlacements…`
  (:132) pre-existing/environment, mechanism undiagnosed → quarantine + trace.
  `WorkProfileLaunchProofTest` environment (profile 150 not in catalogue `[0]`,
  `:platform:launcherapps` untouched by Phase 9) → quarantine as known.
- 10.1b signed rename DONE (`four`, reviewed): `signedFrame→pixelFrame`,
  `signedRestFor→restFor`, `HomeCanvasSignedRendererTest→HomeCanvasRestOffsetTest`
  (git mv); grep zero matches. Unit rerun: grid 130, designsystem 155, home 537,
  app 108, 0 failures.
- 10.4a frontier-test rename DONE (`four`, reviewed): `HomeEditFrontierTest` deleted,
  `HomeAutoScrollTest` (14 zone/interval/viewportRowsFor) + 6 moved to
  `HomeEditStateTest` (tickerRemap ×3, releaseCommit, cancel, noMoveRelease);
  4 deleted as behaviour-duplicates with covering tests named:
  `stationaryRemap_samePointer…` + `stationaryRemap_sameScroll…` → covered by
  `MoveDragMathTest.candidateDependsOnlyOnCurrentPointerAndCurrentScroll`;
  `exitCollapse_scrollClampsToNormalMax` → covered by
  `HomeViewportTest.tallToTall_shrinkRange_bottomPinsToNewBottom`;
  `exitCollapse_fitsViewport_zeroes` → covered by
  `HomeViewportTest.tallToShort_rangeZero_collapsesToZero`.
  `grep HomeEditFrontier` 0 matches. `:feature:home:testDebugUnitTest` 533/0
  (net −4 from 537), `git diff --check` clean. Baseline home count now 533.
- 10.2a STOLEN DONE (`four`, Path A delete, reviewed): `DragExit.STOLEN` +
  `HomeDragDiscard.STOLEN` + `releaseCommit stolen` param + STOLEN branch in
  `resolveDragEnd` deleted (no producer: `trackDrag` returns only
  CANCELLED/LIFTED/STALE, pager never consulted; `HomeDragPagerArbitrationTest`
  proves not-stolen). Deleted tests exercising only the dead symbol:
  `stolenMoveRelease_suppressesCommit`, `stolenResizeRelease_suppressesCommit`
  (HomePointerLoopRegressionTest) + `resolveDragEnd STOLEN→STOLEN` assertion
  (HomeDragRoutingTest:309-311). Covering tests for surviving routes:
  `resolveDragEnd_onlyAVisibleLiftCommits`, `resolveDragEnd_liftWithNothingToCommitIsNoChange`.
  `grep STOLEN` 0 matches. `:feature:home:testDebugUnitTest` 531/0 (net −2),
  `git diff --check` clean. Baseline home count now 531.
- 10.1a-part2 vestigial args DONE (`four`, Path B keep-as-boundary, reviewed):
  `viewportRows`/`workingMarginRows` kept, KDoc rewritten as edit-policy call
  boundary (validated, no math effect); no call-site churn. Boundary test kept:
  `GridVerticalLayoutTest.rejectsInvalidViewportAndWorkingMargin` (sole coverage).
  `:core:grid:test` + `:feature:home:testDebugUnitTest` PASS, `git diff --check` clean.
- 10.6 seeded-home rebuild part 2 DONE (`four`, reviewed + device-evidenced):
  `explicitlySeededEmptyHome…` :86-88 global label-absent → scoped
  `onAllNodesWithTag("home-tile-icon").assertCountEquals(0)` (tag verified at
  `HomeCanvasTile.kt:231`; global check wrong — All Apps pager composes labels);
  both stale test names renamed to `…AndNoHomeRoleOverlay` /
  `…WithNoHomeRoleOverlay`. Device rerun: 33 tests, only the 2 quarantined
  failures (`seededPlacements…` :131, `WorkProfileLaunchProofTest`); both rebuilt
  tests PASS. Stale-name grep 0 matches. `git diff --check` clean.

## Remaining and next action
- Done: 10.1a-part1/part2, 10.1b, 10.4a, 10.6d investigation, 10.2a,
  10.6 seeded-home rebuilds (parts 1+2, device-evidenced 33/2-quarantined).
- Home device suite rerun 2026-10-07: `:feature:home:connectedDebugAndroidTest`
  82/82 PASS (no Phase 10 regression).
- 10.6 trim audit DONE (`zero`, read-only): `HomeCanvasTest` 47 tests inventoried:
  KEEP 2 (#21 sparseDistantRow, #43 unselectedAppOrigin drag-commit), MOVE 27
  (covered by pure suites, named), DELETE 7 (duplicates, covering named),
  UNSURE 11. Gap: 7 of 9 keep-list behaviors have NO device test (only pure
  coverage) — new end-to-end tests must be written, not merely kept.
- Owner decisions on UNSURE 11: KEEP #4 (a11y Role.Button), #5 (click exact
  identity), #28 (single pixel-evidence), #37 (shortcut-tap wiring), #42
  (signature tripwire); DELETE #1/#2/#3 (state-message smoke — covered by :app
  empty-home + HomeRouteLoaderTest), #11 (no-role-overlay — covered by :app
  absence assertions), #12 (region wiring — covered by MoveResizeRoutingTest +
  HomeGestureArbitrationTest), #36 (overlaps kept #5); #21 re-anchor row 16→5.
  Final target ≈ 7 kept + ~8 new keep-list gap tests (H/V/diag move, collision,
  top+right OOB, corner grab, resize, auto-scroll-drag, cancel).
- Trim T1 DONE (`four`, reviewed): 10 edit-entry-family @Tests deleted from
  `HomeCanvasTest` (47→37) with covering pure tests named (HomeEditStateTest,
  HomeGestureArbitrationTest, MoveResizeRoutingTest, CornerControlContractTest,
  HomeRenderProjectionTest); 1 unused import + orphan section comment removed.
  Compile PASS, `git diff --check` clean. Device rerun deferred to post-trim.
- Trim T2 DONE (`four`, reviewed): 10 render/strip/geometry @Tests deleted
  (37→27) with covering suites named (HomeRenderProjectionTest,
  TilePinnedStripTest, HomeCanvasTilePinnedTest); 1 unused import + 2 orphan
  section headers removed. Compile PASS, `git diff --check` clean.
- Trim T3 DONE (`four`, reviewed): 10 @Tests deleted (27→17: 5 duplicates +
  5 pure-covered) with covering tests named; 1 now-unused helper
  (`hasLongClickAction`), 3 unused imports, 1 orphan section header removed.
  Compile PASS, `git diff --check` clean.
- Trim T4 DONE (`four`, reviewed): 10 @Tests deleted (17→7: 2 leftovers +
  6 UNSURE + 2 MOVE-out) with covering tests named; `sparseDistantRow` re-anchored
  row 16→5 (extent 17→6); 5 unused imports removed. Compile PASS,
  `git diff --check` clean. 7 kept: populated a11y, click identity, sparse row-5,
  centered-bounds pixel evidence, shortcut-tap wiring, signature tripwire,
  unselectedAppOrigin drag-commit.
- Next: write ~8 keep-list gap tests (`four`, bounded batches): horizontal move,
  diagonal move, collision rejection, top+right OOB, large-tile corner grab,
  handle resize, downward auto-scroll-drag, gesture cancel. Then final validation
  (assemble, lint, both device suites, audits, unittest discover, diff-check),
  all one command at a time.
- Gap G1 DONE (`four`, reviewed): 4 end-to-end move-drag @Tests appended to
  `HomeCanvasTest` (7→11) via the `onMoveTile` exact-cell / `moves.isEmpty()`
  pattern: `move_horizontalOneCell_requestsExactCellAndKeepsSpan` (1×1 at
  (3,0) → exactly (4,0), span unchanged), `move_diagonalOneCell_requestsExactCellAndKeepsSpan`
  ((0,0) → exactly (1,1), span unchanged),
  `move_intoOccupiedCell_holdsLastValidAndKeepsSpan` (Alpha→Beta cell (3,0)
  collision ⇒ no commit, no resize),
  `move_topAndRightOverflow_rejectedNeverClamped` ((3,1) +5 strides right / −3
  up ⇒ OOB, no commit, never clamped). Compile PASS, `git diff --check` clean.
  Device run deferred to post-trim batch with G2.
- Next: gap batch G2 (`four`, bounded): corner grab without recentering, handle
  resize, downward auto-scroll-drag, gesture cancel. Then final validation
  (assemble, lint, both device suites, audits, unittest discover, diff-check),
  all one command at a time.
- Gap G2 DONE (`four`, reviewed): 3 end-to-end device @Tests appended to
  `HomeCanvasTest` (11→14) + `RESIZE_HANDLE_TAG` import; compile green,
  `git diff --check` clean:
  `resize_handleDrag_requestsNewSpanAndKeepsPosition` (handle tap-arm + drag ⇒
  larger span, `moves.isEmpty()`),
  `move_cornerGrabOnLargeTile_noRecenterTransfer` (2×2 corner-quadrant grab ⇒
  exactly one move commit, no resize),
  `drag_downwardEdgeHold_extendsScrollWhileDragging` (slow 2000 ms bottom-band
  swipe ⇒ valid-cell commit away from origin).
  Test 4 `drag_cancel_restoresCommittedPlacement` NOT added (infeasible):
  `DragExit.CANCELLED` needs pointer-consumed-while-pressed, unreachable via
  `performTouchInput` (auto-lift ⇒ LIFT); BackHandler-interleave impossible in
  synchronous Compose test; OOB-release equivalent already covered by G1
  `move_topAndRightOverflow_rejectedNeverClamped`. Follow-up: real-device
  instrumentation with `sendKeySync` mid-gesture.
- Next: final validation (unit sums, assemble, lint, both device suites, audits,
  unittest discover, diff-check), all one command at a time; then close Phase 10
  exit gate.
- Validation so far: unit sums grid 130 / designsystem 155 / home 531 / app 108,
  0 failures; `assembleDebug` PASS; `lint` PASS; `repo_map`/`module_graph` (no new
  edges)/`function_audit` (signals only)/`compose_audit` (3 benign comment-text
  flags)/`unittest discover` 15/15 OK.
- Home device suite 2026-10-07 (post-trim+G1+G2, 49 tests): 33 failures —
  31 × `IllegalStateException: No compose hierarchies` (activity did not launch;
  whole `EditShortcutMenuComposeTest` minus 1 + whole `HomeAbsoluteLayoutTest` +
  6 `HomeCanvasTest`, incl. untouched classes and Step-Zero-green tests ⇒ harness,
  not product) + 2 real assertions: `move_diagonalOneCell` (0 moves, expected 1)
  and `sparseDistantRow` (row-5 tile displayed initially — T4 re-anchor row 16→5
  wrong for this viewport; needs deeper row). Likely cause of harness wave: the
  §0 wake + dismiss-keyguard step was skipped before this run. Next: wake device,
  dismiss keyguard, rerun home suite; then judge the 2 assertion failures on a
  green harness.
- Device rerun 1 (HomeCanvasTest only, 14 tests): 3 failures, all real, harness
  green. Root causes found by reading `classifyHomeAxis` + viewport math:
  (a) `move_diagonalOneCell` and `resize_handleDrag` swiped perfect 45°
  diagonals, which never leave slop by design (diagonal-never-steals,
  `HomeGestureArbitration.kt:95-101,124-141`) → 0 commits. Fixed by giving both
  swipes a dominant axis: diagonal (2 : 1.25 strides ⇒ exactly (2,1), renamed
  `move_diagonalDominantAxis_requestsExactCellAndKeepsSpan`), resize
  (1.5 : 1 strides). No production change — the tests were driving a path the
  contract refuses. (b) `sparseDistantRow`: T4's row 16→5 re-anchor device-refuted
  (viewport shows ~13 rows on SM-G986W); restored Step-Zero-green row 16 /
  extent 17.
- Device rerun 2 (HomeCanvasTest only): 14/14 PASS. Full home suite rerun:
  49/49 PASS (0 failures). Trim T1–T4 + G1 + G2 device-evidenced with no
  regression vs Step Zero.
- App device suite rerunning (2 shell classes excluded); expect only the 2
  quarantined failures (`seededPlacements` :131, `WorkProfileLaunchProofTest`).
- App device suite 2026-10-07: 33 tests, exactly the 2 quarantined failures —
  `seededPlacementsKeepCatalogueLabelsAndOrderAndOnlyLaunchExactAvailableIdentity`
  (ComposeTimeout 30 s) and `sharedComponentLaunchesInWorkProfile…`
  (`UserHandle{150}` vs catalogue `[0]`). Same 2 names/signatures as quarantine;
  both rebuilt tests from 10.6 parts 1+2 PASS. No Phase 10 regression.
- Phase 10 exit gate CLOSED 2026-10-07: §0 recorded; 10.1a (generic collapse +
  Path B boundary KDoc)/10.1b (pixelFrame/restFor + test rename)/10.2a (STOLEN
  delete)/10.4a (frontier→AutoScroll split, −4)/10.6d (investigation, 2 rebuilds +
  2 quarantines) each decided with covering tests named; `HomeCanvasTest`
  47→14 (T1–T4 deletes with covering suites named, 7 kept + 7 gap: 4 G1 move +
  resize/corner-grab/autoscroll G2; cancel infeasible — needs mid-gesture key
  sync); every surviving test names its invariant. Validation: unit grid 130 /
  designsystem 155 / home 531 / app 108, 0 failures; `assembleDebug` + `lint`
  PASS; home device 49/49; app device 33/2-quarantined; repo/module/function/
  compose audits clean (signals only); `unittest discover` 15/15; `git diff
  --check` clean. One concrete follow-up carried: real-device cancel test
  (`pressBack` mid-gesture via `sendKeySync`); quarantine review for the 2 app
  failures stays with environment, not grid math.
