# 2026-09-24 — Platform catalogue migration to label-bearing LauncherAppEntry
Status: completed (increment 5 first sub-slice; full 0B remains open)
Owner/slice: `:platform:launcherapps`; `docs/agents/tasks/0b-launcher-platform.md` increment 5, first sub-slice.

## Baseline and worktree
Branch `main`, no commits, all files untracked (preserved; nothing staged or committed). Increments 1–4 complete. Started from `2026-09-24-catalogue-labels-in-progress.md`. No prior edits in the worktree beyond earlier increments' untracked sources.

## Completed and decisions
Migrated the platform catalogue contract and pipeline from `AppIdentity` to `LauncherAppEntry` (`identity: AppIdentity` + nonblank `label: String`, owned by `:core:model`, unchanged in this slice).

Touched paths (all under `platform/launcherapps/src/`):
- `main/.../AppCatalogue.kt` — `apps: StateFlow<List<LauncherAppEntry>>` and `cachedApps(): List<LauncherAppEntry>`.
- `main/.../AppInventory.kt` — remains the sole `MutableStateFlow` owner; snapshots stay defensively immutable (`Collections.unmodifiableList`) and deterministic by profile/package/class. Entries are de-duplicated by `AppIdentity`; when one identity appears with competing labels, the lexicographically smallest label wins so the snapshot cannot depend on input order (label is a sort tiebreaker only and is invisible in the published ordering). Profile replacement/removal semantics unchanged.
- `main/.../AppInventoryRefreshWorker.kt` — `fullRefresh: suspend () -> List<LauncherAppEntry>`; serialization, conflation, generation guard and "failed refresh retains last known-good snapshot" behavior unchanged.
- `main/.../LauncherActivityIdentityEnumerator.kt` — reads `LauncherActivityInfo.label` on the background full-refresh path, converts `?.toString()?.trim()`, falls back to the component's packageName when blank; keeps `distinctBy { it.identity }` with package/class ordering. No `LauncherActivityInfo`, `CharSequence`, `Drawable` or `UserHandle` leaves the boundary; `UserHandle` remains transient in `ProfileTokenAdapter`.
- `main/.../LauncherAppsAppCatalogue.kt` — `apps`/`fullRefresh` type migration only; lifecycle, callback/receiver registration, failure cleanup and launch contract untouched.

Tests (platform only):
- `test/.../AppInventoryTest.kt` — 12 tests (was 8): all prior invariants migrated to entries, plus deterministic collapse of competing labels, order-independent de-duplication, retained profile-aware identity and nonblank label.
- `test/.../AppInventoryRefreshWorkerTest.kt` — 8 tests migrated to `LauncherAppEntry`; published snapshot's identity/label asserted on the refresh path.
- `androidTest/.../LauncherActivityIdentityEnumeratorTest.kt` — asserts nonblank labels equal to the trimmed framework label or packageName fallback (cross-checked per component against `getActivityList`), deterministic across repeated calls, duplicate-free identities, retained profile token/component, and framework-free declared field shape (`LauncherAppEntry.identity: AppIdentity`, `label: String`).
- `androidTest/.../LauncherAppsAppCatalogueTest.kt` — asserts nonblank labels, retained component/profile tokens, one entry per identity, deterministic profile/package/class ordering, and framework-free entry field declarations.

Decision: class/file names (`LauncherActivityIdentityEnumerator`, `AppInventoryRefreshWorker`) were kept; only types and contracts migrated, per the assigned scope.

## Validation
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :core:model:testDebugUnitTest` — PASS (UP-TO-DATE, module untouched). Existing executed results: 6 tests (`AppIdentityTest` 3, `LauncherAppEntryTest` 3), 0 failures/errors/skipped; actual tests, not NO-SOURCE.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :platform:launcherapps:testDebugUnitTest` — PASS. 23 tests: `AccessibleProfileMappingTest` 3, `AppInventoryRefreshWorkerTest` 8, `AppInventoryTest` 12; 0 failures/errors/skipped.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :platform:launcherapps:connectedDebugAndroidTest` — PASS on device SM-G986W (Android 13, serial `R5CN70E2SGV`): 6 tests across 6 classes, 0 failures/errors/skipped (results XML `platform/launcherapps/build/outputs/androidTest-results/connected/debug/`). Harness printed a benign `appops set androidx.test.services … No UID … user 0` (exit 255) line before the run; the suite and build succeeded.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :platform:launcherapps:lint` — PASS. 5 dependency-version warnings only (`GradleDependency`/`NewerVersionAvailable`): compileSdk 36→37, `androidx.test:runner` 1.6.2→1.7.0, `androidx.test.ext:junit` 1.2.1→1.3.0, `kotlinx-coroutines-core`/`-test` 1.9.0→1.11.0. All pre-existing and located in `platform/launcherapps/build.gradle.kts` (out of scope). No code findings from this slice.
- `python3 tools/scripts/repo_map.py` — PASS; `python3 tools/scripts/module_graph.py` — PASS (graph unchanged: `:platform:launcherapps -> :core:model` etc.); `python3 tools/scripts/function_audit.py` — clean; `python3 tools/scripts/compose_audit.py` — clean.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :app:compileDebugAndroidTestKotlin` — FAIL (expected, out-of-scope caller, not edited): `app/src/androidTest/java/me/foxtails/lakehead/LauncherAppsAppLifecycleTest.kt:39:17 Return type mismatch: expected 'AppIdentity', actual 'LauncherAppEntry'`. `:app:compileDebugKotlin` and `:feature:home:compileDebugKotlin` passed in the same invocation, so app/feature main sources are unaffected.

Device evidence: platform instrumentation suite passed on a real device. Not run: `:app` instrumentation (its androidTest does not compile until the fix below); HOME selection, work-profile launch, package-mutation callback delivery remain unverified device behavior for later increments.

## Remaining and next action
Known incomplete work:
1. `app/src/androidTest/java/me/foxtails/lakehead/LauncherAppsAppLifecycleTest.kt:38-40` binds `val identity: AppIdentity = catalogue.apps.first { it.isNotEmpty() }.first()`; after the mandated contract migration this must read `… .first().identity` (or type the val as `LauncherAppEntry` and pass `.identity` to `LauncherAppsActivityLauncher`). Owner `:app`; I was scoped out of `app/`.
2. `docs/agents/tasks/0b-launcher-platform.md` status line still says the label sub-slice "starts" now; owner should refresh it (docs out of this subagent's scope).
3. Full 0B exit gate open: work-profile launch verification, package-mutation callback delivery, user-visible app list (feature), settings remain open.

Next action: assign `:app` the one-line `LauncherAppsAppLifecycleTest.kt` fix; exit condition: `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :app:compileDebugAndroidTestKotlin` passes and `:app:connectedDebugAndroidTest` is green on device. Then continue increment 5's next sub-slice (label consumption in the app list presentation, owner `:feature:home` per parent plan) without changing the platform contract again.
