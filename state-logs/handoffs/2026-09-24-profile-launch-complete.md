# 2026-09-24 — Complete increment 4 correct-profile launch boundary
Status: completed
Owner/slice: `:platform:launcherapps` plus thin `:app` wiring; `docs/agents/tasks/0b-launcher-platform.md` (increment 4)

## Baseline worktree
Increment 4 was completed and validated on branch `main`; no commits, all files untracked. Preserve the worktree. Increments 1–4 are complete; the whole 0B exit gate remains open.

## Completed decisions
Touched paths: new `AppLauncher`, `LauncherAppsActivityLauncher`, and launcher instrumentation test; app build wiring and `MainActivity`; direct app target-UID integration test; `MainActivityCatalogueLifecycleTest` and internal lazy catalogue property.

The public launch contract accepts only `AppIdentity` and returns `Boolean`. The platform implementation resolves the exact accessible `ProfileId`, verifies `ComponentName` membership, and invokes `LauncherApps.startMainActivity`; Android types including `UserHandle` remain platform-only. `MainActivity` owns one lazy `lifecycleScope` catalogue and starts/stops it in `onStart`/`onStop`; the Activity owns no inventory.

Target-UID launch, personal-profile launch success, and stale/fake identity rejection are verified. No claim is made that work-profile behavior or the complete 0B gate has been validated.

## Validation
- `./gradlew assembleDebug` — PASS.
- `./gradlew test` — PASS: 22 JVM tests (core 3, platform 19); other modules NO-SOURCE.
- `./gradlew lint` — PASS with advisories.
- Connected platform instrumentation — PASS, 6 tests / 0 failures.
- Connected app instrumentation — PASS, 2 tests / 0 failures. Non-fatal `appops` warning: `androidx.test.services` UID.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS.
- `python3 tools/scripts/function_audit.py` — PASS.
- `python3 tools/scripts/compose_audit.py` — PASS.
- Device: SM-G986W, API 33. Work-profile launch, package mutation callback delivery, a user-visible app list, and settings remain unverified/open.

## Remaining next action
Increment 5, one smallest slice only: extend platform catalogue entries with immutable framework-resolved labels needed for display while preserving app identity/profile and the single inventory owner. Exit condition: deterministic label-bearing entries with focused tests, and no UI work. Whole 0B gate remains open until its outstanding behavior is independently implemented and evidenced.

Worktree/commit state: branch `main`, no commits, all files untracked; preserve the worktree.
