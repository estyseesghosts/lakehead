# 2026-09-24 — D3 app migration and production route wiring
Status: in progress
Owner/slice: `:app` migration and `:feature:home` presentation cleanup — [`d3-viewport-aware-home-rendering.md`](../../docs/agents/tasks/d3-viewport-aware-home-rendering.md)

## Baseline and worktree

The repository baseline and current worktree are all-untracked; `git status --short` showed only `??` paths. Nothing was staged and no commit exists. The source changes listed below were made earlier in this slice; no additional source edits, staging, or commits were made during the final validation/documentation pass. The APK remains installed on SM-G986W / API 33.

## Completed and decisions

The app migration and Home presentation cleanup are implemented. Production Home now uses `HomeCanvas` via the app-owned loader and decoded-icon boundary, replacing the Phase A Home catalogue presentation. App-owned paths added/changed for this slice:

- `app/src/main/java/me/foxtails/lakehead/MainActivity.kt`
- `app/src/main/java/me/foxtails/lakehead/HomeRouteLoader.kt`
- `app/src/main/java/me/foxtails/lakehead/DecodedHomeIconCache.kt`
- `app/build.gradle.kts`
- `app/src/test/java/me/foxtails/lakehead/HomeRouteLoaderTest.kt`
- `app/src/androidTest/java/me/foxtails/lakehead/MainActivityAppLaunchTest.kt`
- `app/src/androidTest/java/me/foxtails/lakehead/MainActivityPackageMutationTest.kt`

The old Home-only presentation/test paths removed are `feature/home/src/main/java/me/foxtails/lakehead/feature/home/Home.kt`, `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCatalogueList.kt`, `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCatalogueState.kt`, and `feature/home/src/androidTest/java/me/foxtails/lakehead/feature/home/HomeCatalogueTest.kt`. The replacement feature paths are `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvas.kt`, `HomeCanvasGrid.kt`, `HomeCanvasIcons.kt`, `HomeCanvasTile.kt`, `HomeRenderProjection.kt`, `HomeRenderState.kt`, `feature/home/src/test/java/me/foxtails/lakehead/feature/home/HomeRenderProjectionTest.kt`, and `feature/home/src/androidTest/java/me/foxtails/lakehead/feature/home/HomeCanvasTest.kt`.

Ownership/contract decisions: app wiring alone constructs and supplies the repository to Home; Room and `:data:repository` remain implementation owners and the repository is consumed only through its existing D2 load contract. The route supplies explicit `HomeId("home-main")`; it does not create/provision a Home, populate placements, or turn catalogue entries into placements. D2's contract is unchanged. The profile-aware platform catalogue and inventory remain owned by `:platform:launcherapps` for metadata and future All Apps. No D4+ work was started.

## Validation

Focused and repository validation results:

- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :feature:home:testDebugUnitTest` — PASS, 25 tests.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :feature:home:connectedDebugAndroidTest` — PASS, 15/15 tests on SM-G986W / API 33.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :data:repository:testDebugUnitTest` — PASS, 9 tests.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :platform:launcherapps:testDebugUnitTest` — PASS, 35 tests.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :app:testDebugUnitTest` — PASS, 6 HomeRouteLoader tests.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew assembleDebug` — PASS.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew test` — PASS; `:fixtures:launchable:testDebugUnitTest` was NO-SOURCE.
- `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew lint` — PASS.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS.
- `python3 tools/scripts/function_audit.py` — PASS, no output.
- `python3 tools/scripts/compose_audit.py` — PASS, no output.
- `git diff --check` — PASS, no output.

Install/package evidence:

- `./gradlew :app:assembleDebug` — initial FAIL because no SDK location was set (`SDK location not found`). Retried as `ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :app:assembleDebug` — PASS, `BUILD SUCCESSFUL`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — PASS, exact output `Success`.
- `adb shell pm path me.foxtails.lakehead` — `package:/data/app/~~q1gDhur2zSS3AzNn6XA1Mw==/me.foxtails.lakehead-JMV2IJ0qmaPqWpVG-yaKSg==/base.apk`.
- `adb shell dumpsys package me.foxtails.lakehead` — package present; `versionName=0.1.0`, `versionCode=1`.
- `adb shell getprop ro.product.model` — `SM-G986W`; `adb shell getprop ro.build.version.sdk` — `33`.

The APK is left installed. Installation and package inspection do not establish runtime UI behavior; no Activity was manually launched and HOME was not selected.

## Remaining and next action

D3 remains in progress. Production-device evidence is not recorded for an explicitly persisted populated Home, loaded-empty Home, not-found Home, sparse scrolling, floating overlay behavior, or real profile-aware launch. No Home provisioning/population contract exists. Manual accessibility and visual checks (TalkBack speech, focus order, font scaling, reduced motion, screenshots) remain unverified. The launch-loop report remains deferred and unreproduced; it was not investigated here.

Next action: obtain explicit commit authorization, then run focused production-device verification using an explicitly supplied `HomeId` and record the D3 gate evidence. Exit condition: the required populated/empty/not-found, sparse-scroll, overlay, and profile-aware launch behavior is actually verified and recorded, along with outstanding manual accessibility/visual checks; do not start D4 before authorization/commit and the full D3 gate.

## Worktree state

Only the requested state documentation was edited in this handoff; no staging or commit occurred. Baseline/current worktree is all-untracked.
