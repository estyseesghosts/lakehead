# 2026-09-23 — Add bounded HOME eligibility and user-triggered default-role request
Status: in progress
Owner/slice: `:app` (manifest and RoleManager mechanics) and `:feature:home` (event/presentation); `docs/agents/tasks/0b-launcher-platform.md` (increment 1)

## Baseline and worktree
Verified baseline: branch `main` has no commits and the whole tree is untracked. Preserve unfamiliar files; do not treat the untracked tree as disposable. This is the initial in-progress handoff for a bounded HOME-eligibility increment.

## Completed and decisions
Touched paths:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/me/foxtails/lakehead/MainActivity.kt`
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomePlaceholder.kt`

The merged manifest has separate MAIN + LAUNCHER and MAIN + HOME + DEFAULT filters. `MainActivity` uses the Activity Result API plus an availability check for `RoleManager.ROLE_HOME`. The feature exposes only an accessible callback and contains no Android role mechanics. A targeted architecture review found no blockers. Scope remains this bounded HOME-eligibility/default-role-request increment; it does not claim the whole 0B launcher-platform gate is complete.

## Validation
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew assembleDebug` — PASS.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew test` — PASS; all module unit tests were NO-SOURCE.
- `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew lint` — PASS.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS; zero violations and `app → feature` preserved.
- `python3 tools/scripts/function_audit.py` — PASS; no findings.
- `python3 tools/scripts/compose_audit.py` — PASS; no findings.
- Debug APK and merged manifest exist.
- Device — NOT RUN pending connection. Role dialog, role-holder result, press appearance and TalkBack remain unverified.

## Remaining and next action
Next action: connect a device, install and launch the APK, trigger the control, and capture role-holder evidence. Exit condition: captured result or a precise blocker. Do not infer device behavior from static validation.
