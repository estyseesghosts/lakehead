# 2026-09-24 — Define profile-aware installed-app identity and catalogue contract
Status: in progress
Owner/slice: `:core:model` identity and `:platform:launcherapps` catalogue contract; `docs/agents/tasks/0b-launcher-platform.md` (increment 2)

## Baseline and worktree
The baseline worktree is on branch `main`, has no commits, and all files are untracked. Preserve all files. Increment 1 is complete with device evidence documented in [`2026-09-24-home-eligibility-complete.md`](2026-09-24-home-eligibility-complete.md). The whole 0B exit gate remains open.

## Completed and decisions
This is the baseline handoff for increment 2; no implementation is claimed. Intended scope is a framework-free profile-aware installed-app identity in `:core:model` and a narrow `:platform:launcherapps` catalogue contract capable of representing the same package/component in two profiles distinctly. No LauncherApps enumeration, callbacks, launch, UI, or persistence is in scope yet.

## Validation
- Validation for this increment: NOT RUN.

## Remaining and next action
Increment 2 remains in progress. Next action: focused contract exploration for the identity and catalogue boundary, owned by `:core:model` and `:platform:launcherapps`, before implementation. Exit condition: focused tests prove identity preservation and inequality across two profile identifiers, and architecture/build validation passes. The whole 0B gate remains open.
