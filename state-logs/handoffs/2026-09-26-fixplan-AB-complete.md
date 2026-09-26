# 2026-09-26 — Fix-plan Phase 0 closeout + Phase A (gating) and Phase B (tile rework) implemented

Status: code + unit validation complete; device proof NOT-RUN for all of Phase A and Phase B
Owner/slice: `:feature:home`, `:core:designsystem`, `:app` — per `docs/Oneshot/00/00_fixplan.md` (fix-plan Phase 0/A1-A3/B1-B4; distinct from roadmap Phase A/0B and Phase B/grid)

## Baseline and worktree
Prior handoff `2026-09-26-phase3-4-complete.md` left W2+W4 uncommitted. Fix-plan Phase 0 closed that out (`3fc4aea`) so gesture work started committed. Branch `main`; HEAD `0959b0b`.

## Completed
Commits in order: `3fc4aea` Phase-0 closeout, `5eec785` A1 hold gate, `8937e95` A2 holdsPager arbitration, `5a014ec` A3 bottom-33pct region, `e6b0115` B1 swipe removal, `d47fa7c` B2 move restore, `0dd00ec` B3 handle harden, `0959b0b` B4 EditShortcutMenu.
- A1: isHomeHold + homeHold ANDed into userScrollEnabled, empty/dispose false-reset hoisted above early-return.
- A2: holdsPager 6 true/2 false, snapshot-once per gesture, no mid-flip, IdleHitSnapshot in rememberUpdatedState.
- A3: isBottomRegionStart pure + pagerRegionAllowed latch ANDed, pill bypasses gate.
- B1: flick/show deleted (history KDoc only), swipeSurfaceFrame retained for B4.
- B2: isOnHandle=startInHandle only, unpinIfSelected+cancel on remove stays-in-edit.
- B3: ResizeHandle visual-only KDoc, 10 new tests, GridRules sole legality.
- B4: EditShortcutMenu sibling above selection, follows selectedId, none->no menu, zero-activation dismiss, ShortcutSurfaceRow reuse, thin surfaceEntries pass-through (production empty until platform:shortcuts gateway — partial deferral).
- Ownership: transient in feature:home, pager gates in app, legality in core:grid. No new modules/edges, app->feature->core intact.

## Validation
- ./gradlew assembleDebug SUCCESS (303 tasks)
- ./gradlew test SUCCESS (221 tasks, 898 tests 0 fail: 388 feature:home incl rerun, 141 designsystem incl rerun; fixtures NO-SOURCE)
- ./gradlew lint SUCCESS (360 tasks, 0 errors, pre-existing warnings only)
- repo_map PASS, module_graph 21 unique edges no new, function_audit 4 pre-existing only, compose_audit 1 pre-existing false-positive only. Zero signals in touched files.
- Focused: :feature:home:testDebugUnitTest SUCCESS, :core:designsystem:testDebugUnitTest SUCCESS.
- NOT-RUN device (SM-G986W R5CN70E2SGV): all Phase A+B proofs (edit swipe block, tile vs gap paging, top vs bottom region, flick nothing, move commit, resize block, menu above). B4 9 compose tests in androidTest NOT-RUN (needs assembleDebugAndroidTest + device).

## Remaining and next action
- Device validation Phase A+B on SM-G986W, record PASS/FAIL per case.
- Then fix-plan Phase C C1->C2->C3 feed host (Lawnfeed/Neo Feed).
- Doc drift: docs/Oneshot/00/00_fixplan.md:20 still quotes pre-closeout state — follow-up.
- Next action: device agent runs Phase A+B manual proofs starting A2/A3 + B1. Exit when every proof recorded PASS/FAIL.
