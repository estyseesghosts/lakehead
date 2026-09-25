# Slice 0B — launcher platform

**Status:** Complete; Phase A / full 0B exit gate passed. A2, A3, A4, and the full device/static gate are complete with recorded evidence. A5 settings persistence remains an explicitly user-approved deferral and is not a Phase A implementation task.

## Exit gate for the whole slice

On device, ProtoLaunch must be selectable as HOME; display installed launchable activities with component and profile identity; launch selected activities in the correct profile; and update the visible inventory after installs and removals without restarting. Verify accessible work-profile launch where a work profile is available. Capture device evidence; unit tests and builds alone are insufficient. **Gate passed:** all applicable device evidence and required static validation are recorded in `state-logs/handoffs/2026-09-24-phase-a-complete.md`. On Samsung API 33 without a work profile, A4 explicitly skips; the separate PLG110 API 36 proof completes A4.

Settings persistence is not part of the 0B exit gate. Android owns current HOME-role/default-launcher selection. The user-approved A5 deferral stands: do not implement speculative settings in Phase A; introduce settings persistence with the first real setting-backed behavior and its owning data contract.

## Dependency and state ownership

- `:app` owns HOME manifest/activity wiring and thin composition integration, not catalogue logic.
- `:core:model` owns framework-free profile-aware app identities.
- `:platform:launcherapps` owns `LauncherApps` enumeration, the installed-app inventory, callback invalidation, and launches. Do not replace it with package-name-only `PackageManager` inventory.
- `:feature:home` owns transient presentation state and receives the narrow `HomeCatalogueState`/event contract; it does not access `LauncherApps`, `Context`, `UserHandle`, or platform inventory internals.
- `HomeCatalogueState` represents initial catalogue loading as `Loading` and ready catalogue data as `Content(apps: List<LauncherAppEntry>)`. A loaded empty catalogue is `Content(emptyList())`, not loading.
- `:fixtures:launchable` is isolated Android test infrastructure for the A3 package-mutation proof. Production code must not depend on it.
- Settings persistence remains deferred to a future dedicated data owner; do not add speculative 0B settings.

## A2/A3 implementation evidence

### A2 — user-visible catalogue presentation — complete

**Owners:** `:feature:home` presentation; thin `:app` wiring.

The feature presents a custom visible app list with labels, stable profile-aware identity, accessible click semantics, and explicit loading and empty states. It emits launch requests through `onLaunchApp: (AppIdentity) -> Unit`; application wiring invokes the existing launcher. Five connected feature tests and the app tap-identity instrumentation test passed on API 33. Visible labels/loading/empty/click semantics were verified. No persisted grid is part of A2.

**Device evidence:** On SM-G986W (API 33), ProtoLaunch displayed installed launchable activities with visible labels; tapping an entry launched it. This proves personal-profile interaction only and does not close A4.

### A3 — live package mutation — complete

**Owners:** `:platform:launcherapps` callback behavior; `:app` and `:feature:home` visible-state observation.

The isolated `:fixtures:launchable` fixture is installed/uninstalled with real package-manager commands. On SM-G986W (API 33), its addition appeared and removal disappeared from the visible catalogue while the same ProtoLaunch Activity instance stayed alive; neither Activity recreation nor launcher restart occurred. This is framework-delivery evidence, not direct callback invocation. The production manifest uses a narrow `MAIN`/`LAUNCHER` `<queries>` declaration for launchable activity visibility.

Prepare the device using exactly these commands:

```text
env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :fixtures:launchable:assembleDebug
adb push fixtures/launchable/build/outputs/apk/debug/launchable-debug.apk /data/local/tmp/protolauch-a3-fixture.apk
```

Keep ProtoLaunch alive, install `/data/local/tmp/protolauch-a3-fixture.apk` with `pm install`, verify it appears, uninstall the fixture using its actual application ID with `pm uninstall`, and verify it disappears. Do not recreate the Activity between these observations.

## Completed increments and gate evidence

### A4 — correct-profile device proof — complete

On PLG110 (API 36), personal profile 0 and managed work profile 11 exposed shared `com.android.contacts/.DialtactsActivityAlias` as distinct identities. Launch of the selected work identity was observed under `u11`. An inaccessible profile token returned false and produced no new personal ActivityRecord. Direct proof is split between `WorkProfileLaunchProofTest.kt` and `WorkProfileDeviceEvidence.kt`; function audit was clean. On Samsung API 33 without a work profile, the A4 test explicitly skips.

### A5 — user-approved settings deferral

**Status: Deferred by user approval.** Do not implement settings as part of Phase A/0B. Android owns HOME role selection; do not persist a duplicate role preference. Begin settings work only with a real setting-backed behavior and an explicit owning data contract.

### Full Phase A / 0B gate — complete

The remaining device and static-validation evidence passed:

- On SM-G986W, process recreation changed PID 5710 → 5797; `MainActivity` resumed and catalogue labels were visible after restart.
- Samsung TalkBack smoke passed with Samsung TalkBack enabled. UI hierarchy exposed clickable/focusable actions and rows, and keyboard focus traversal worked. Speech output was not verified.
- HOME-role evidence had shown ProtoLaunch as sole holder on PLG110 before later test cleanup. Current PLG default state after cleanup is not claimed.
- Full Samsung connected app suite: 5 total; 4 passed, 1 A4 skip because no work profile was present.
- Mandatory static suite passed: `assembleDebug`, `test` (29 unit tests), `lint` (0 errors, 31 warnings), `repo_map.py`, `module_graph.py`, `function_audit.py`, and `compose_audit.py`.
- The `appops set ... androidx.test.services` warning was harness-only; product has no external-storage permission.

Phase A / 0B is complete. Phase B may begin with B1; no later phase is claimed complete.

## Suggested order

1. Phase B B1 — coordinate, span, and collision model in `:core:grid`.
2. Continue with B2 vertical extent and B3 column adaptation only after the B1 slice is independently verified.

A2, A3, A4, and the full Phase A gate are complete and are not pending work. Settings implementation is not in the Phase A order; the user-approved deferral remains in force.
