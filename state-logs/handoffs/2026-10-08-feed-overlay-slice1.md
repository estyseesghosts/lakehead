# 2026-10-08 — Feed overlay repair slice 1 (host data URI on discovery)
Status: completed
Owner/slice: `:platform:feed` + `:app` manifest wiring / `docs/agents/tasks/feed-overlay-repair.md` slice 1

## Baseline and worktree
- Started from `fb9d3bb` with unrelated uncommitted `:feature:home` work (other sessions) plus untracked `docs/fixfeed.md` (diagnostic input, left untracked). Slice-1 work touched only the paths below; nothing else.
- Slice 0 (task spec) done in this session: `docs/agents/tasks/feed-overlay-repair.md` covers slices 1–3, defers 4–9 to `docs/fixfeed.md`.
- Reference pinned during this session: Lawnchair `LauncherClient.getIntent` at commit `505dbc40e6154c05158b5d0271c45f6a885a411b` builds the overlay intent data as `app://<hostPackage>:<uid>?v=7&cv=9` via `Process.myUid()`. Providers match on scheme `"app"` only.

## Completed and decisions
- NEW `platform/feed/.../FeedHostUri.kt`: framework-free builder returning exactly `app://<pkg>:<uid>?v=7&cv=9`; rejects blank package / negative uid. Pure Kotlin so JVM unit tests pin the string without Robolectric (`android.net.Uri` returns defaults under unit tests).
- `AndroidFeedServiceQuery.probe`: query intent gains `.setData(Uri.parse(FeedHostUri.build(...)))` next to `setPackage(provider)`; KDoc updated.
- `AndroidFeedPreflight.verify` (in `FeedOverlayGatewayImpl.kt`): same data URI on its intent. Nothing else in that file touched.
- `app/src/main/AndroidManifest.xml`: `<data android:scheme="app" />` added inside the WINDOW_OVERLAY `<queries>` intent.
- `platform/feed/build.gradle.kts`: `testInstrumentationRunner` + `androidTestImplementation` (runner, ext:junit, core, coroutines-test) for the device gate.
- NEW `platform/feed/src/androidTest/.../FeedOverlayDiscoveryDeviceTest.kt` (3 tests: Neo Feed probe returns `com.saulhdev.feeder.manager.service.OverlayService`; resolver returns a target; gateway reaches `Available`) plus `src/androidTest/AndroidManifest.xml` mirroring the production queries block so the test APK can see providers.
- Deliberately excluded (slices 2+): the bind intent (`AndroidFeedConnector.bind`) still carries no data; no diagnostics surfacing.
- Notable device finding: Lawnfeed's package exposes `amirz.aidlbridge.BridgeService` under WINDOW_OVERLAY/scheme-app (not a separate Lawnfeed class), so the "4 providers" are Google, aidlbridge, Lawnfeed(via BridgeService), Neo Feed. Gateway `Available` was reached with Lawnfeed installed (priority order), i.e. `bindService` acceptance did not need the data URI — connection-stage behavior is slice 2's question.

## Validation
- `./gradlew :platform:feed:testDebugUnitTest` (forced rerun): 170 tests, 0 failures (11 new `FeedHostUriTest`).
- `./gradlew test` (full): BUILD SUCCESSFUL.
- `./gradlew assembleDebug`: BUILD SUCCESSFUL. `git diff --check` clean.
- `./gradlew lint` (full, twice) + `:platform:feed:lintDebug :app:lintDebug` rerun: BUILD SUCCESSFUL, exit 0.
- `repo_map.py` / `module_graph.py`: only pre-existing `:app -> :platform:feed` edge, no new edges. `function_audit.py`: only pre-existing flags. `compose_audit.py`: no feed flags.
- Device (serial R5CN70E2SGV = SM-G986W, Android 13, Lawnfeed + Neo Feed + aidlbridge + Google installed): `:platform:feed:connectedDebugAndroidTest` 3/3 PASS (`probeForNeoFeedReturnsOverlayService`, `resolverReturnsTargetOnDevice`, `gatewayReachesAvailableWithInstalledProvider`).
- Shell probes: `cmd package query-services -a WINDOW_OVERLAY -d 'app://me.foxtails.lakehead:11080?v=7&cv=9'` lists DrawerOverlayService, BridgeService ×2, Neo Feed OverlayService; data-less query returns "No services found" (pre-fix behavior reproduced).
- NOT RUN: provider-alone matrices, install/removal live re-resolution, release-variant device run (slice 9 matrix).

## Remaining and next action
- Next: slice 2 — set the same `FeedHostUri` data on `AndroidFeedConnector.bind`'s explicit-component intent (`FeedOverlayGatewayImpl.kt:1838`); extend the device test to assert `bindingEvidence` accepted+connected for Neo Feed alone and Lawnfeed alone, recording any provider rejection with its diagnostic cause. Exit: device run shows accepted+connected per provider (or a recorded cause). Then slice 3 (setprop-gated log + settings footer). Do not start slices 4+ before re-checking flags/call order against the pinned reference client.
