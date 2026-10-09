# 2026-10-07 — Fix scrolled drag coordinate double-count

Status: completed
Owner/slice: `:feature:home`, task `docs/agents/tasks/move-drag-repair.md`

## Baseline and worktree
Started at HEAD `4b6948b`. Worktree had uncommitted shortcut-menu edits (`EditShortcutMenu.kt`, `EditShortcutMenuTest.kt`, `EditShortcutMenuComposeTest.kt`, `CURRENT.md` update, `2026-10-07-shortcut-menu-cap.md`); left untouched except CURRENT pointer update per protocol. No commits/staging by this session.

## Completed and decisions
Boundary conversion in `feature/home/.../HomeGridGestures.kt` only; `MoveDragMath`, `HomeEditState`, `HomeAutoScroll`, `HomeGridMetrics.contentY` contracts unchanged (rejected moving modifiers outside `verticalScroll` — arbitration risk):
- Added `nodeLocalToViewportY(nodeYPx, scrollYPx) = nodeYPx - scrollYPx` with KDoc (modifiers after `verticalScroll` yield content-space Y).
- `awaitDownTile`: viewport-convert once, then `contentY(viewport, scroll)` for `tileAt`, both `isInHandle` checks, `grabOffsetYPx`. `DragDown.position` stays node-local so slop deltas (same-space, no scroll between) are unaffected.
- `updateDrag`: reads `scrollYPx` once, converts node→viewport, then `onMovePointer` holds true viewport Y and `moveTo`/`resizeTo` apply scroll exactly once. Covers both initial slop/latest offsets and every `trackDrag` loop event exactly once — no separate slop-return conversion (avoids double-convert).
- `homePagerGesture`: same node→viewport fix for the idle `tileAt` hit-test.
- Checked other consumers: `tapCancelModifier` (no math), `HomeGridOverlay`/`EditShortcutMenu` (visible-window math only, no pointer re-offset) — no change.
- JVM: new `HomeGestureCoordinatesTest` (finger-on-tile yields cell for several S; lift zero at down for several S; top edge viewport vs content zone) + 2 regression tests in `HomeGridGeometryTest` (`nodeLocalToViewportY`, scrolled node-local down recovers tile, double-count drifts).
- Device: 2 new tests in `HomeAbsoluteLayoutTest` — `scrolledDrag_byOneStride_commitsExactlyOneRow` (scroll ~3 rows, drag mid row 5 by one stride → commits (0,6)) and `scrolledHoldAtTopEdge_scrollsUp` (3-row scroll, hold y=4 → scroll decreases). Note: 5-row scroll variant placed the grabbed tile at the viewport top and flaked; 3-row keeps the down outside the edge band and is stable.

Touched paths:
- `feature/home/src/main/.../HomeGridGestures.kt`
- `feature/home/src/test/.../HomeGridGeometryTest.kt`
- `feature/home/src/test/.../HomeGestureCoordinatesTest.kt` (new)
- `feature/home/src/androidTest/.../HomeAbsoluteLayoutTest.kt`

## Validation
- `./gradlew assembleDebug`: PASS.
- `./gradlew test`: PASS (all modules; `:feature:home` 562 tests, 0 failures/errors — was 557, +5 new).
- `./gradlew lint`: PASS.
- `python3 tools/scripts/repo_map.py`: PASS, no action.
- `python3 tools/scripts/module_graph.py`: no new edges (`:feature:home` → core only).
- `python3 tools/scripts/function_audit.py`: review-candidates only; `HomeCanvasGrid` composable still flagged (pre-existing, 498-line scope), no new gesture flags.
- `python3 tools/scripts/compose_audit.py`: only pre-existing comment-word hits.
- `git diff --check`: clean.
- Device R5CN70E2SGV: `:feature:home:connectedDebugAndroidTest` full module 53/53 PASS (was 51, +2 new). Targeted `HomeAbsoluteLayoutTest` 11/11 PASS on re-run (one transient full-class run reported a failure that did not reproduce; final full-module + targeted runs green). Single new top-edge test also PASS in isolation.

## Remaining and next action
Real-finger pass on R5CN70E2SGV per `move-drag-repair.md` acceptance: scroll-then-drag follow, top/bottom edge auto-scroll while scrolled, drop glide. Exit: scrolled drag commits under finger and edge holds track, or a fix slice is opened with observed geometry. Shortcut-menu Slice 4 manual pass (problem tile) still needs the user's tile/data.
