# 2026-10-07 — Release builds with release optimizations

Status: completed; merged to main as `4b6948b` (merge of `c0d91ba`).
Owner/slice: `:app` Gradle build script; task spec
`docs/agents/tasks/release-builds-setup.md`.

## Baseline and worktree

- Started from `c2613b7` (Phase 10 close-out commit on `main`). No
  uncommitted changes were carried in: this work is on a separate
  `release-builds` branch in `.worktrees/release-builds/`, isolated
  from the Phase 10 dirty worktree on `main` (orchestrator directive:
  "current slice already finished, youre good").
- Branch: `release-builds` (deleted after merge).
- Worktree path
  `/Users/terezi/AndroidStudioProjects/lakehead/.worktrees/release-builds`
  (removed via `git worktree remove` + `git branch -d release-builds`
  after the merge).
- `local.properties` created (gitignored) pointing at
  `~/Library/Android/sdk` for Gradle SDK discovery.
- `~/.android/debug.keystore` already exists on this machine; the
  release variant re-uses it (no committed secret).
- Module list at start (per `settings.gradle.kts`):
  `:app, :core:model, :core:navigation, :core:grid,
  :core:designsystem, :platform:launcherapps, :platform:shortcuts,
  :platform:feed, :feature:home, :feature:allapps,
  :fixtures:launchable, :data:database, :data:repository` —
  unchanged.

## Completed and decisions

- `:app/build.gradle.kts`: added a `release` build type with
  `isMinifyEnabled = true`, `isShrinkResources = true`,
  `isDebuggable = false`, `isCrunchPngs = true`, R8 full mode, and a
  `signingConfig` aliased to `signingConfigs.debug`. The debug
  build type block is recorded explicitly for symmetry. No new Gradle
  module added. Module dependency graph unchanged.
- `:app/proguard-rules.pro` (new): minimal consumer rules keeping
  Room (`PlacementDatabase`, `*_Impl`, entities, DAOs, `@TypeConverter`),
  kotlinx-coroutines `MainDispatcherFactory`, and `@Keep`-annotated
  members. Documentation-only notes explain why we do **not** add
  blanket `androidx.compose.runtime.**` keeps (Compose Compiler emits
  the rules it needs at packaging) and why we do **not** keep
  instrumented `*Test` classes (they are not on the dexed release
  classpath).
- `gradle.properties`: `android.enableR8.fullMode=true` (explicit
  even though it is the AGP 9 default).
- `.gitignore`: `keystore.properties`, `*.jks`, `*.keystore` added so
  the future real-keystore switch has a place to live without
  leaking.
- `keystore.properties.example` (new, committed template only): a
  documented env-driven consumer for the future real Play upload key.
  No real values. Inline example wiring is included as a comment in
  the file so future agents do not have to re-derive the integration.

Decisions:

- **Re-use the debug keystore for `release` in this slice.** No real
  upload keystore handling — that is an explicit follow-up (Play
  upload secrets must not be committed; see AGENTS.md §7).
- **Same `applicationId` as debug**, no `applicationIdSuffix`. Room
  schema migration `MIGRATION_1_2` runs against either, and a release
  install overwrites the debug install cleanly for the smoke test.
- **No per-library `consumer-rules.pro` added in this slice.**
  AGENTS.md §3 forbids speculative extension points. The Room block
  here is sufficient evidence so far; library-level rules are a
  follow-up if evidence later demands them.
- **`isDebuggable = false` on release.** AGENTS.md §4 "no in-process
  debugger" invariant.

## Validation

Single-shell, one invocation per command (per AGENTS.md §6):

- `./gradlew :app:assembleRelease` → **PASS** in 34 s,
  `app/build/outputs/apk/release/app-release.apk` produced (2 004 143
  bytes), `app/build/outputs/mapping/release/` populated
  (`configuration.txt`, `mapping.txt`, `seeds.txt`, `usage.txt`).
  `:app:minifyReleaseWithR8` ran with `proguard-android-optimize.txt`
  + `proguard-rules.pro`.
- `./gradlew :app:bundleRelease` → **PASS** in 21 s, AAB at
  `app/build/outputs/bundle/release/app-release.aab` (2 682 032 bytes).
- `./gradlew :app:lintRelease` → **PASS** in 47 s. Lint report at
  `app/build/reports/lint-results-release.html` shows **16 warnings,
  0 errors**; all warnings are pre-existing
  (`OldTargetApi`, `GradleDependency`, `AndroidGradlePluginVersion`,
  `NewerVersionAvailable`, `DataExtractionRules`,
  `NotShrinkingResources`, `MissingApplicationIcon`,
  `UnsafeOptInUsageWarning`). No new R8 / `MissingClass` /
  `UnknownAttribute` flags introduced by this slice.
- `./gradlew :app:testDebugUnitTest
  :feature:home:testDebugUnitTest
  :core:grid:test
  :core:designsystem:test
  :data:repository:test
  :data:database:testDebugUnitTest` → **PASS** in 33 s, all 6 modules.
- `./gradlew assembleDebug lint` → **PASS** in 35 s, no regression.
- `python3 tools/scripts/repo_map.py` → unchanged from baseline.
- `python3 tools/scripts/module_graph.py` → no new edges; the
  cross-module dependency direction is unchanged
  (`app → feature → core`, `app → platform → core`,
  `core:grid → core:model`, `data:repository → data:database →
  core:model/grid`).
- `python3 tools/scripts/function_audit.py` → unchanged from
  baseline (still flags `HomeCanvasGrid` 487 lines, etc. — pre-
  existing).
- `python3 tools/scripts/compose_audit.py` → unchanged from
  baseline (pre-existing `HomeCanvasTest.kt` test-class
  `LauncherApps` string literal that the auditor pattern-matches).
- Mapping inspection: `seeds.txt` confirms `PlacementDatabase_Impl`,
  `PlacementDao_Impl`, every `*Entity`, `*Dao`, and `MainActivity`
  are kept by name. `configuration.txt` shows 64 `-keep` rules total
  (R8 + AGP + our 12).

Device smoke (R5CN70E2SGV already attached, `adb` available):

- `adb -s R5CN70E2SGV install -r -t
  app/build/outputs/apk/release/app-release.apk` → **PASS**
  ("Success"); package state shows
  `firstInstallTime=lastUpdateTime=2026-10-07 15:09:28`, confirming
  the release APK replaced any prior install. Signing signature
  present (`signatures=PackageSignatures{...version:2,
  signatures:[3a06c18d], past signatures:[]}`).
- `adb -s R5CN70E2SGV shell pm set-home-activity
  me.foxtails.lakehead/.MainActivity` → **PASS** ("Success").
- `adb -s R5CN70E2SGV shell am start -n
  me.foxtails.lakehead/.MainActivity` → started; process
  `me.foxtails.lakehead` PID 29720 alive. `mCurrentFocus =
  Window{b0b8c0d u0 me.foxtails.lakehead/me.foxtails.lakehead.MainActivity}`.
- `adb -s R5CN70E2SGV logcat -d -t 200` → no `FATAL`,
  no `AndroidRuntime`, no `ClassNotFoundException` /
  `NoSuchMethodError`. R8 has not broken anything reflectively
  loaded at startup.
- Screenshot via `adb exec-out screencap -p` shows the Home screen
  rendering with the navigation pills (F / H / A), the H pill in the
  accent colour, and the expected "Home not found" empty-state
  message (no `placements.db` exists on this fresh install, which
  matches the 0B exit-gate evidence on a clean device). Compose
  runtime, theme, and `TileColorRole` resolution all executed under
  R8 — confirmed not stripped.

## Remaining and next action

- The release APK is currently installed on `R5CN70E2SGV` and HOME
  is set to `me.foxtails.lakehead/.MainActivity`. This is a
  side-effect of the smoke test; leaving it in place is the
  documented state for the next agent. If a fresh device profile is
  desired, `adb uninstall me.foxtails.lakehead` clears both.
- No follow-up is required for this slice to be considered complete,
  but recorded as deferred per the task:
  - **Real Play upload keystore.** Add `keystore.properties`
    (gitignored) + activate the wiring snippet in
    `keystore.properties.example`. Slice will then need a re-signed
    AAB + `bundleRelease` rerun.
  - **R8 baseline profiles** for startup-critical paths. Defer until
    we have a startup-time regression to fix; AGENTS.md §3 forbids
    speculative extension points.
  - **`versionCode` / `versionName` automation** per release
    channel. Defer until we have a real distribution channel.
  - **Crash-reporting / mapping-upload wiring.** Defer until we have
    a real distribution target.
  - **Per-library `consumer-rules.pro`**, especially if Room rules
    evolve. Add when evidence demands.

Exit condition for the next agent: this slice is closed. The next
implementation session should pick up from
`docs/first_draft.md` / `state-logs/CURRENT.md` once the orchestrator
restores the main worktree (or moves the worktree to its own session).