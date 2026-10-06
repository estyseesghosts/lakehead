# 2026-10-06 — Home grid repair Phase 3: authoritative `HomeEditState` (slices 3.1–3.4)
Status: in progress
Owner/slice: `:feature:home`; spec `docs/lakehead-grid-repair-plan.md` Phase 3 (task: `docs/agents/tasks/grid-repair-phase0.md`).

## Baseline and worktree
HEAD `3b1c59a`. Worktree dirty (~115 Phase 0–2 and concurrent paths), nothing staged. `:core:grid` and `:core:model` are not touched in this phase. Phase 2 closed at unit level only; nothing has been run on a device.

Verified at session start (read from code, not from the plan):
- `HomeCanvasGrid.kt` is 2,744 lines in total (`wc -l`); the `HomeCanvasGrid()` composable spans lines 177–1897 (1,721 lines, per `function_audit.py`). Baseline for "must not grow": 2,744 file lines / 1,721 function lines.
- Baseline `./gradlew :feature:home:testDebugUnitTest --rerun-tasks --console=plain -q` at session start: PASS (see result XML check below).
- Edit state is split across `HomeEditSession` (Idle/Active, mixed position+span+mode), `HomeInteractionController` (Normal/Editing(selectedId, resizeArmed)), the Compose `editSession` var, `LiveEditSessionRef.session`/`.moveFrame` (synchronous mirror) and `editFrontier`/`pendingMove` (legacy legality/lease, Phase 7/9).
- All users of these types are inside `:feature:home` (`HomeCanvasGrid`, `HomeCanvasTile`, `EditShortcutMenu`, `CandidateOverlays`, `HomeEditFrontier`, `MoveDragMath`, and ~10 unit test files plus `HomeCanvasTest` androidTest).

## Slice exit conditions (copied from the plan)
- 3.1: MOVE state cannot mutate span; RESIZE state cannot mutate position; selection, resize arming and current drag have one authoritative owner.
- 3.2: Candidate update has no O(tile-count) DTO conversion/allocation beyond rectangle collision against the stable occupancy collection.
- 3.3: Most editor behavior is covered by normal Kotlin tests with no Compose runtime.
- 3.4: A search for “authoritative” comments is no longer needed to explain which edit-state copy wins.

## Decisions so far
- Plan §5.3 (not Phase 3) assigns freezing the *rendered* rest offset (`originAnim`) during an edit. Phase 3 therefore cannot make the live rest immutable without changing rendering. `HomeEditState` stores the metrics a drag began with and no transition except an explicitly named temporary `rebaseMetrics` bridge changes them; the bridge replaces `shouldRefreezeMoveFrame` / `refreezeMoveFrameIfNeeded` and is to be deleted in Slice 5.3/9.1.

## Completed
(none yet)

## Validation
(none yet)

## Remaining and next action
Slice 3.1: see Completed once filled in.
