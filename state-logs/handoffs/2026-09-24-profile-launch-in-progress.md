# 2026-09-24 — Begin increment 4 correct-profile launch boundary
Status: in progress
Owner/slice: first bounded slice in `:platform:launcherapps`; `docs/agents/tasks/0b-launcher-platform.md` (increment 4)

## Baseline and worktree
Baseline is branch `main`, no commits, and all files untracked; preserve the worktree. Increments 1–3 are complete, documented in [`2026-09-24-home-eligibility-complete.md`](2026-09-24-home-eligibility-complete.md), [`2026-09-24-profile-identity-complete.md`](2026-09-24-profile-identity-complete.md), and [`2026-09-24-launcherapps-catalogue-complete.md`](2026-09-24-launcherapps-catalogue-complete.md). The full 0B exit gate remains open. The device has one accessible personal profile only; no work-profile proof is available.

## Completed and decisions
This is a baseline handoff only; no implementation or source/task-spec changes are claimed. Intended scope is limited to defining and implementing a framework-facing correct-profile launch boundary in `:platform:launcherapps`: accept `AppIdentity`, resolve the accessible profile through `ProfileTokenAdapter`, convert `ComponentIdentity` to `ComponentName`, then invoke `LauncherApps.startMainActivity`. The boundary must fail safely for inaccessible, stale, or mismatched identities and must not let `UserHandle` escape the platform module. No app/UI wiring is part of this first sub-slice.

## Validation
- Increment 4 validation — NOT RUN.
- No device changes or launch behavior were exercised for this baseline.

## Remaining and next action
Implement only the bounded `:platform:launcherapps` launch boundary and add focused JVM/instrumentation proof that a resolvable accessible profile identity launches its own component, inaccessible/stale/mismatched identities fail safely, and `UserHandle` never escapes the platform. Device proof can only cover the available personal profile; do not claim work-profile proof. Exit this sub-slice when those tests pass and the platform boundary's identity and failure behavior are verified; the complete 0B gate remains open.

Worktree/commit state: branch `main`, no commits, all files untracked; preserve. Increment 4 validation remains NOT RUN.
