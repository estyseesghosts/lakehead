# 2026-09-24 — Explore LauncherApps catalogue identity and implementation seams
Status: in progress
Owner/slice: `:platform:launcherapps`; `docs/agents/tasks/0b-launcher-platform.md` (increment 3)

## Baseline and worktree
Branch `main`, no commits, and all files untracked; preserve the existing worktree. Increments 1 and 2 are complete in [`2026-09-24-home-eligibility-complete.md`](2026-09-24-home-eligibility-complete.md) and [`2026-09-24-profile-identity-complete.md`](2026-09-24-profile-identity-complete.md), respectively. The whole 0B launcher-platform gate remains open.

## Completed and decisions
This is a baseline in-progress handoff only; no implementation or exploration findings are claimed. Intended scope begins with exploration: determine a supported normal-launcher stable `ProfileId` mapping from `LauncherApps` `UserHandle`, accessible-profile enumeration, cache/callback invalidation ownership, and test seams. Known blocker: public serial permission behavior is unconfirmed. Do not claim enumeration or callback implementation in this phase.

## Validation
Increment 3 validation: NOT RUN. No device behavior is established by this baseline.

## Remaining and next action
Increment 3 remains in progress and the full 0B gate remains open. Next action: focused exploration by `:platform:launcherapps` of the `LauncherApps` identity mapping, accessible-profile enumeration, cache/callback invalidation ownership, and test seams. Exit condition: document a concrete device-supported stable mapping and a decomposed increment-3 implementation plan; do not claim enumeration/callback implementation yet.

Worktree/commit state: branch `main`, no commits, all files untracked; preserve.
