# 2026-09-24 — Complete HOME eligibility and default-role request increment
Status: completed
Owner/slice: `:app` and narrowly `:feature:home`; `docs/agents/tasks/0b-launcher-platform.md` (increment 1)

## Baseline and worktree
The worktree is on branch `main`, has no commits, and the whole tree remains untracked. Preserve all files; captures are ignored. This handoff closes only 0B increment 1. The full 0B exit gate remains open.

## Completed and decisions
Touched paths:
- `app/src/main/AndroidManifest.xml` — retains MAIN + LAUNCHER and adds a separate MAIN + HOME + DEFAULT filter.
- `app/src/main/java/me/foxtails/lakehead/MainActivity.kt` — uses Activity Result and checks `RoleManager.ROLE_HOME` availability before requesting.
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomePlaceholder.kt` — exposes only an accessible callback trigger; contains no `RoleManager`.

Target review found no blocking boundary issue. This increment adds no catalogue, launch path, package/profile callbacks, grid or persistence.

## Validation
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew assembleDebug` — PASS.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew test` — PASS; every module unit test was NO-SOURCE.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew lint` — PASS.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS; zero violations and `app → feature` preserved.
- `python3 tools/scripts/function_audit.py` — PASS; no findings.
- `python3 tools/scripts/compose_audit.py` — PASS; no findings.
- Device SM-G986W, API 33: `adb install -r app/build/outputs/apk/debug/app-debug.apk` — Success; app launched. UIAutomator exposed the exact clickable text and bounds. Filtered logcat showed `PermissionController RequestRoleActivity` started for `android.app.role.REQUEST_ROLE` from ProtoLaunch and no `AndroidRuntime` exception. `adb shell dumpsys role` showed user 0 `android.app.role.HOME` holder `me.foxtails.lakehead`; pressing HOME resumed `me.foxtails.lakehead/.MainActivity`. Screenshots under ignored `captures/` show the prompt and settled placeholder. `cmd role get-role-holders` is unavailable on this API 33 image, so `dumpsys role` was used. TalkBack was not exercised.

## Remaining and next action
The whole 0B exit gate remains incomplete: catalogue display, correct-profile launching, package/profile callback updates, and persisted basic settings are not implemented/verified. Next action: implement 0B increment 2 only, beginning with the framework-free profile-aware app identity model and catalogue contract under `:core:model` / `:platform:launcherapps`. Exit condition: the same component/package can be represented distinctly in two profiles, with focused tests proving identity preservation.
