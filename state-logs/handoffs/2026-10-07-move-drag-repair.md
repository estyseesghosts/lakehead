# 2026-10-07 — Repair Home tile move/drag
Status: completed (real-finger pass pending)
Owner/slice: `:feature:home`, `:app` reconciliation; `docs/agents/tasks/move-drag-repair.md`

## Baseline and worktree
Started at `df8c7bb` on `main`. Nothing pushed. Commits: f1b697e (gesture start), c66237a (nearest-cell snap), 2f11e16 (lifted tile follows finger), 31551f9 (continuous auto-scroll, capped workspace), 3c348d3 (normalised drop target, glide), plus the final tests/spec commit.

## Completed and touched paths
- Gesture start: `HomeGridGestures.kt` (no-slop long-press pickup, radial slop).
- Snap: `HomeGridGeometry.kt`, `MoveDragMath.kt`.
- Follow: `HomeCanvasTile.kt`, `HomeCanvasGrid.kt`, `HomeGridOverlay.kt`.
- Auto-scroll: `HomeAutoScroll.kt`, `HomeEdgeAutoScroll.kt`, `HomeEditWorkspace.kt`. Frames are requested only while the pointer is in a band with room to scroll.
- Honest drop: `HomeGridEditor.kt`, `CandidateOverlays.kt`, `PlacementGlide.kt`, `app/.../HomePlacementReconciliation.kt`, `HomeRouteLoader.kt`.
- Tests: unit and androidTest updated and added.

## Decisions
- The workspace is capped at committed bottom + 2 rows + span, so short content gains no scroll range.
- The ghost and `withRequestedMove` both use `GridRules.move` + `GridRowGapNormalizer.normalize`.

## Validation
- `./gradlew assembleDebug test lint -q`: pass.
- `./gradlew :feature:home:connectedDebugAndroidTest -q` on R5CN70E2SGV: pass.
- `repo_map.py` and `module_graph.py`: not run.
- `function_audit.py` and `compose_audit.py`: run; review candidates only, no new module edges.

## Unverified
- Real-finger behaviour on device.
- Whether pointer positions are viewport-local after scrolling. If not, scroll is double-counted in lift and candidate. Check by scrolling, then dragging.

## Next action
Run a real-finger pass on R5CN70E2SGV (pickup, follow, edge scroll, drop glide, rejected glide-back, scroll-then-drag). Exit: all behave per the task spec, or a fix slice is opened.
