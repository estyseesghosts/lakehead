# 2026-09-24 — Define profile-aware installed-app identity and catalogue contract
Status: completed
Owner/slice: `:core:model` identity and declaration-only `:platform:launcherapps` contract; `docs/agents/tasks/0b-launcher-platform.md` (increment 2)

## Baseline and worktree
The worktree is on branch `main`, has no commits, and all files are untracked. Preserve all files. Increment 1 is complete with device evidence documented in [`2026-09-24-home-eligibility-complete.md`](2026-09-24-home-eligibility-complete.md). Increment 2 is complete; the full 0B exit gate remains open.

## Completed and decisions
Implemented framework-free profile-aware app identity and a declaration-only catalogue contract. Touched paths: `core/model/build.gradle.kts`; new `ProfileId.kt`, `ComponentIdentity.kt`, `AppIdentity.kt`, and `AppIdentityTest.kt`; `platform/launcherapps/build.gradle.kts`; and new `AppCatalogue.kt`.

`ProfileId` is a nonblank opaque platform-assigned token, stable for the profile lifetime and across process restarts, resolved only at the platform boundary. `:core:model` exposes no `UserHandle` or other Android types. `AppIdentity` combines profile and flattened component identity and preserves both when the component is equal. `AppCatalogue.cachedApps` returns a read-only view of the last published cache, or empty before discovery; it performs no enumeration and has no update notifications in this increment. Enumeration, callbacks, and launching remain deferred. Architecture review found two moderate contract ambiguities; both were fixed and re-reviewed with no blocking findings.

## Validation
- Focused `:core:model:testDebugUnitTest`: PASS.
- Direct XML test report: 3 tests, 0 failures, 0 errors, 0 skipped.
- `:platform:launcherapps:assembleDebug`: PASS.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew assembleDebug`: PASS.
- `./gradlew test`: PASS; 3 real core tests, other modules NO-SOURCE.
- `./gradlew lint`: PASS.
- `python3 tools/scripts/repo_map.py`: PASS.
- `python3 tools/scripts/module_graph.py`: PASS; platform depends on core only, with no leakage.
- `python3 tools/scripts/function_audit.py`: PASS, no findings.
- `python3 tools/scripts/compose_audit.py`: PASS, no findings.
- No device behavior was required or claimed for this contract-only increment.

## Remaining and next action
One blocker remains before enumeration/callback cache implementation: increment 3 must prove a supported normal-launcher mapping from accessible `UserHandle` to stable `ProfileId`; serial permission behavior is not yet established. The full 0B gate remains open. Next action: focused increment-3 exploration of `LauncherApps` accessible-profile enumeration, stable profile-token production/resolution, cache/callback ownership, and test seams. Owner: `:platform:launcherapps`. Exit condition: identify a device-supported mapping and produce a bounded implementation plan.

Worktree/commit state: branch `main`, no commits, all files untracked; preserve the worktree.
