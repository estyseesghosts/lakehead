# 2026-09-24 — Implement LauncherApps catalogue and invalidation
Status: completed
Owner/slice: `:platform:launcherapps`; `docs/agents/tasks/0b-launcher-platform.md` (increment 3)

## Baseline and worktree
Increment 3 started from the in-progress exploration handoff. The worktree remains on branch `main`, has no commits, and all files are untracked; preserve it. Increments 1–3 are complete, but the overall 0B launcher-platform exit gate remains open. This closeout records the completed catalogue/invalidation slice only; it does not claim HOME app wiring, user-visible catalogue presentation, correct-profile launch, or settings persistence.

## Completed and decisions
Implemented the `:platform:launcherapps` catalogue and invalidation slice. Production paths touched:
- `platform/launcherapps/build.gradle.kts`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/AccessibleProfileMapping.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/AppCatalogue.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/AppInventory.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/AppInventoryRefreshWorker.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/LauncherActivityIdentityEnumerator.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/LauncherAppsAppCatalogue.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/LauncherAppsInvalidationCallback.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/ProfileLifecycleInvalidationReceiver.kt`
- `platform/launcherapps/src/main/java/me/foxtails/lakehead/platform/launcherapps/ProfileTokenAdapter.kt`

Added/updated three JVM tests: `AccessibleProfileMappingTest.kt`, `AppInventoryTest.kt`, and `AppInventoryRefreshWorkerTest.kt`; and five instrumentation tests: `LauncherActivityIdentityEnumeratorTest.kt`, `LauncherAppsAppCatalogueTest.kt`, `LauncherAppsInvalidationCallbackTest.kt`, `ProfileLifecycleInvalidationReceiverTest.kt`, and `ProfileTokenAdapterTest.kt` (all under the corresponding `src/test` or `src/androidTest` package directory).

Decisions and ownership:
- `AppInventory` is the sole owner of `MutableStateFlow`; it publishes immutable, deterministic inventory snapshots through `StateFlow`.
- Accessible profiles must map completely to stable profile tokens or mapping fails; do not publish a silently partial inventory.
- The public concrete catalogue runtime accepts an application `Context` and caller-owned `Job` scope. `start`/`stop` manage callback and context-receiver registration; registrations are released on stop, scope completion, and partial-start failure.
- Package/activity and profile lifecycle invalidation use coarse full refreshes rather than maintaining a second, incremental catalogue state.
- Profile token conversion uses public serial APIs and is restricted to users returned as accessible LauncherApps profiles. Activity enumeration uses `LauncherApps` and produces framework-free identity; package, availability and suspension invalidation is through `LauncherApps.Callback`.
- Profile lifecycle invalidation uses API-gated protected broadcasts registered with `RECEIVER_EXPORTED`. Failed refreshes retain the last known-good inventory.

Targeted review fixes/verification: confirmed single mutable inventory-flow ownership, immutable deterministic snapshots, complete mapping failure rather than partial publication, cleanup on normal and partial lifecycle exits, and retention of last-good state on refresh failure. Final targeted review verdict: PASS; ownership remains `:platform:launcherapps` with a platform-to-core dependency only, and no 0B gate was overstated.

## Validation
- `./gradlew assembleDebug` — PASS.
- `./gradlew test` — PASS; 22 real tests, 0 failures.
- `./gradlew lint` — PASS, exit 0. Warnings remain: platform report has 5 dependency advisories; app report has 7 existing advisories, including target/dependency/backup/icon warnings.
- `./gradlew connectedDebugAndroidTest` — PASS; 5 tests, 0 failures on SM-G986W, API 33. Non-fatal warning: appops reported an `androidx.test.services` UID issue. These instrumentation tests ran under the test-library UID, not ProtoLaunch HOME's UID.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS; platform depends on core only.
- `python3 tools/scripts/function_audit.py` — PASS; no findings.
- `python3 tools/scripts/compose_audit.py` — PASS; no findings.

Device limitations: no work profile was available/proven, no real profile lifecycle broadcast delivery or package mutation callback delivery was exercised, and no correct-profile activity launch was demonstrated. The connected instrumentation result does not establish HOME-UID behavior.

## Remaining and next action
The full 0B gate remains open: wire the catalogue into the app, display a user-visible app list, prove actual install/removal callback delivery, launch an activity in its correct profile on device, and persist basic settings. Do not claim these based on the catalogue tests.

Next action: implement increment 4 as one bounded `:platform:launcherapps` slice—a correct-profile launch contract/adapter accepting `AppIdentity`, resolving its profile through `ProfileTokenAdapter`, and launching its component with `LauncherApps.startMainActivity`. Exit when an accessible profile identity resolves and its component launches on device without exposing `UserHandle`; add focused device proof. Keep app UI/wiring, settings persistence and the other open exit-gate items outside this slice.

Worktree/commit state: branch `main`, no commits, all files untracked; preserve. No device changes were made for this closeout.
