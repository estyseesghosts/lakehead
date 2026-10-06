---
# 2026-09-30 — move-velocity runaway fix
Status: implementation and automated validation complete; manual verification pending
Owner/slice: `:feature:home` only — move-velocity runaway fix

## Baseline and worktree
The velocity guard changes are present as modified files in the worktree. Concurrent work was preserved; no unrelated changes were overwritten.

## Completed and decisions
The ticker is now the sole driver of edge-hold movement. The guard uses a 32dp edge band, 200ms dwell, and proportional timing from 120–300ms. The event-loop double-step was removed.

Touched paths:
- `HomeCanvasGrid.kt` ticker lines 609-763 and drag loop lines 1564-1638
- `HomeEditFrontier.kt` lines 21-30 and 216-238, plus its helper
- `HomeEditFrontierTest.kt` lines 316-355 policy tests

Decisions: use one movement driver; use proportional rather than fixed timing; retain the 32dp band; preserve remap and attachment behavior. No changes to `:core:grid`, `:app`, or persistence.

## Validation
- `HomeEditFrontierTest` — BUILD SUCCESSFUL
- `assembleDebugAndroidTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL, 363 tasks, 6 executed; real pass
- `:app:installDebug` on SM-G986W — Installed on 1 device; real pass
- No hierarchies blocker encountered

Gesture feel still needs manual verification. The P3 dwell re-entry caveat is noted.

## Remaining and next action
Known incomplete work: the smallTile product corner (180 vs 144) remains open; 02 remainder A3/B1/B2/C/D/E remains open; manual edge-hold feel is unverified.

One next action: on SM-G986W, hold a tile at the edge and confirm slow proportional scrolling, then confirm scrolling stops on exit or lift. Exit when confirmed or when a bounded failure is filed.

Exit gate: manual edge-hold behavior is confirmed on device, or a bounded device failure is documented for follow-up.
---
