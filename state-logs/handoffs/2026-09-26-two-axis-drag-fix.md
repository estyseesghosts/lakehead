# 2026-09-26 — two-axis drag mapping and viewport wiring
Status: completed; device-pending
Owner/slice: `feature:home` viewport/drag wiring

## Baseline and worktree
The implementation is represented by commits `f073d2b` (pure viewport/scroll/grab math) and `f8bdc22` (HomeCanvasGrid wiring). Unrelated worktree changes were preserved.

## Completed and decisions
Pointer-to-content mapping now snapshots scroll/grab state, preserves both x and y components for diagonal movement, and uses floor row inversion. Resize applies scroll correction, and gesture arbitration is diagonal-safe. Existing legality and last-valid snapback behavior remain in place. No new gesture or persistence owner was introduced.

## Validation
Home unit: 397 passed (cached/up-to-date). Compile, `assembleDebug`, `test`, and `lint` passed. `repo_map` and `module_graph` passed. Function/Compose audits reported only pre-existing findings.

No device verification was performed. Actual up/down/diagonal movement, collision snapback, resize, and edge controls remain unverified.

## Remaining and next action
User/device verification of free 2D movement and the listed interaction cases is the next action. Exit when any concrete device failure is reported as a bounded follow-up; do not claim device proof before then.
