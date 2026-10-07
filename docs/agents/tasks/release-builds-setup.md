# Release builds with release optimizations

Status: planned
Phase: post-0B (build-pipeline milestone, before store distribution)
Owner: `:app` Gradle build script; `:core:*` / `:feature:*` / `:platform:*` / `:data:*` untouched

## Objective and boundary

Make `./gradlew :app:assembleRelease` and `./gradlew :app:bundleRelease` produce a
R8-minified, resource-shrunk, debug-keystore-signed release artifact for
ProtoLaunch. The release variant must build cleanly with no
`ClassNotFoundException` / `NoSuchMethodError` once side-loaded onto
`R5CN70E2SGV`. This is a build-pipeline slice — it changes Gradle config and
shipping rules; it does **not** introduce new product features, change
`:core:model` / `:core:grid` / `:feature:*` / `:platform:*` / `:data:*`, or
commit the debug keystore.

Out of scope (recorded as deferred follow-ups):

- A real Play upload keystore (env-driven, no committed secrets).
- R8 baseline profiles for startup-critical paths.
- `versionCode` / `versionName` automation per channel.
- ProGuard rule hardening beyond the **minimum** needed to keep Compose,
  Room, Kotlin coroutines, kotlinx-serialization-free runtime, and our
  annotation-processed Room DAO intact.
- Crash-reporting / mapping-upload wiring.

## Scope

In scope:

- `:app/build.gradle.kts`: add a `release { }` build type with
  `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode
  (`android.enableR8.fullMode=true` in `gradle.properties`),
  `signingConfig` pointing at the local debug keystore (so the slice
  produces a runnable APK without committing a release key), and a
  `proguardFiles` line that merges `getDefaultProguardFile("proguard-android-optimize.txt")`
  with `proguard-rules.pro`.
- `:app/proguard-rules.pro`: minimal **consumer** rules needed to keep
  R8 from breaking runtime correctness for the libraries this app
  actually pulls in:
    - Keep `androidx.room.RoomDatabase` and the generated `*_Impl`
      subclasses (Room's own rules normally cover this, but the
      `@TypeConverters` on `PlacementDatabase` benefit from an explicit
      keep on `PlacementDatabase` and `PlacementDatabase_Impl`).
    - Keep `kotlinx.coroutines.android.AndroidDispatcherFactory` and the
      `kotlinx.coroutines.internal.MainDispatcherFactory` SPI — required
      so `Dispatchers.Main` resolves on a R8-stripped build.
    - Keep `@androidx.annotation.Keep`-annotated members and any
      lifecycle-coroutine continuation bridges.
    - Keep the entire `me.foxtails.lakehead.feature.home.HomeCanvasTest`
      and instrumentation-only `*Test` classes if they leak through R8
      (they do not — instrumented tests are in a separate variant — so
      the rule is documented but a no-op; we keep the note so future
      agents do not chase the false-positive lint).
- `gradle.properties`: `android.enableR8.fullMode=true` (R8 default for
  AGP 9 already, but explicit is cheaper than chasing silent regressions
  if the project ever flips the default).
- `app/build/outputs/mapping/<variant>/` (generated; not committed).
- `keystore.properties.example` (committed, **example** only, ignored
  shape): documents the future real-keystore wiring, no real values.
  Documented in the handoff and AGENTS.md §7 forbids committing real
  secrets — `local.properties` and `keystore.properties` are gitignored.
- A `release-builds-smoke` device pass: install the release APK onto
  `R5CN70E2SGV`, start it as HOME, place one tile, and verify it
  launches the seeded activity. This is the only evidence that R8 has
  not stripped something we actually use.

## Ownership and dependency boundaries

- The `:app` build script is the only file that knows the **keystore
  credentials** source (debug keystore for now, env-driven later). No
  `:core:*` / `:platform:*` / `:data:*` / `:feature:*` Gradle script
  is modified.
- Library modules do **not** add `consumerProguardFiles` in this
  slice. AGENTS.md §3 forbids speculative extensions; if R8 regression
  evidence later shows a library truly needs consumer rules, that
  library grows its own `consumer-rules.pro` in its own slice.
- `core:designsystem`, `core:grid`, `core:model`, `core:navigation`,
  `feature:home`, `feature:allapps`, `platform:launcherapps`,
  `platform:shortcuts`, `platform:feed`, `data:database`,
  `data:repository` — no edits.
- The debug keystore is loaded by `~/.android/debug.keystore` (Android
  SDK default), which already exists on this machine
  (`ls ~/.android/debug.keystore` → present). The build script does
  **not** read or write that file. The Android Gradle Plugin auto-
  creates one if missing.
- `signingConfig.release` is wired with the debug keystore explicitly
  (`storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")`).
  The future real-keystore path is documented in
  `keystore.properties.example` and is **not** activated in this slice.

## Release variant specification

- `isMinifyEnabled = true` and `isShrinkResources = true` for the
  `release` build type only. The `debug` build type is unchanged
  (AGENTS.md §4 forbids fast iteration on debug-time-only paths).
- `isDebuggable = false` for `release` (no in-process debugger; matches
  AGENTS.md §4 "no in-process debugger" invariant).
- `isCrunchPngs = true` for `release` (we already encode PNG snapshots
  in `:platform:launcherapps`; this is cheap and correct).
- `applicationIdSuffix = ".release"` is **off**; we want the same
  application id so the release APK replaces the debug install on the
  test device without removing the persisted database (Room schema
  migration `MIGRATION_1_2` runs either way).
- No `versionNameSuffix` (release carries the same version metadata
  for now; bump `versionCode` if we ship more than one release APK).
- ProGuard files:
    - `getDefaultProguardFile("proguard-android-optimize.txt")`
    - `"proguard-rules.pro"`
- `signingConfig`: `signingConfigs.debug` aliased into the release
  build type for now. Documented in the file as the only line that
  has to change when the real keystore lands.

## Validation

Run these in order, one shell command per invocation per AGENTS.md §6.
Record `PASS` / `FAIL` / `NOT RUN` for each.

```text
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
./gradlew :app:lintRelease
./gradlew :app:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :core:grid:test
./gradlew :core:designsystem:test
./gradlew :data:repository:test
./gradlew :data:database:testDebugUnitTest
./gradlew assembleDebug
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Device validation (R5CN70E2SGV already attached):

```text
adb -s R5CN70E2SGV install -r -t app/build/outputs/apk/release/app-release.apk
adb -s R5CN70E2SGV shell am start -n me.foxtails.lakehead/.MainActivity
# HOME role acceptance test follows existing 0B device tests in
# :app:connectedDebugAndroidTest. We do not run those in this slice
# because they target the debug variant; the smoke is documented in
# the handoff with `NOT RUN` if the device step is blocked.
```

A successful `assembleRelease` does **not** prove runtime correctness;
the device smoke is the actual gate.

## Acceptance criteria

- `./gradlew :app:assembleRelease` produces
  `app/build/outputs/apk/release/app-release.apk` with no warnings about
  missing ProGuard rules for code we ship.
- `./gradlew :app:bundleRelease` produces
  `app/build/outputs/bundle/release/app-release.aab`.
- The release APK installs onto `R5CN70E2SGV` and can be set as HOME
  (HOME default-role flow exercises the same code paths as the debug
  build).
- One seed-launched activity still launches under the correct profile.
- `:app` lint passes with no new `MissingClass` / `UnknownAttribute` /
  `Proguard`-flagged warnings caused by this slice.
- All previously green unit + instrumentation tests remain green
  (debug variant unchanged).
- No new Gradle module added (AGENTS.md §3).
- No committed secrets (`local.properties`, `keystore.properties`,
  `*.jks`, `*.keystore` are ignored via `.gitignore`).
- `keystore.properties.example` documents the future env-driven
  signing config but no file consumes it in this slice.

## Required tests

No new automated tests. The acceptance criteria is the device smoke
plus `lintRelease` cleanliness. AGENTS.md §5 forbids tests that merely
echo the implementation; the existing debug-variant tests already
exercise the same code paths R8 will shrink.

## Explicitly deferred

- Real Play upload keystore handling (env-driven, no committed
  secrets). Future slice activates `keystore.properties` consumption.
- R8 baseline profiles for startup-critical paths.
- `versionCode` / `versionName` per-channel automation.
- Crash-reporting / mapping-upload wiring.
- Per-library `consumer-rules.pro`. If evidence shows one is needed,
  that library grows its own in its own slice.
- Hermes / ART optimizations (Compose-relevant but Compose Compiler
  already handles them through AGP defaults).

## Narrow exit gate

Complete when `assembleRelease` and `bundleRelease` both succeed with
R8 full mode and resource shrinking on, the produced APK installs and
runs on `R5CN70E2SGV` as HOME without R8-stripping breakage, no
Gradle module is added, no secret is committed, and the worktree
only touches additive build files (`app/build.gradle.kts`,
`app/proguard-rules.pro`, `gradle.properties`, and the example
signing template) plus this task spec and the matching handoff.

## Completion evidence

- `assembleRelease` and `bundleRelease` PASS.
- `lintRelease` PASS.
- All debug-variant unit suites remain green
  (`:core:grid 184`, `:core:designsystem 155`, `:feature:home 531`,
  `:data:repository`, `:data:database`; `:app:testDebugUnitTest`).
- Device smoke `install -r` + HOME launch PASS, or `NOT RUN` with
  exact failure mode recorded.
- A handoff in `state-logs/handoffs/YYYY-MM-DD-release-builds-setup.md`
  records baseline, touched paths, decisions, exact validation,
  unverified device behavior, and one concrete next step.
- `git diff --stat` against the slice-start branch shows only the
  intended files.