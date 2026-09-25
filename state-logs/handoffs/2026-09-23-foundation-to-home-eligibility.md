# 2026-09-23 — foundation handoff to HOME eligibility

Status: governance complete; implementation of 0B not started.
Owner/slice: next owner `:app` for the first, narrowly bounded step of [`docs/agents/tasks/0b-launcher-platform.md`](../../docs/agents/tasks/0b-launcher-platform.md).

## Baseline and worktree

Git repository exists on branch `main` with **no commits**. `git status --short` shows the project files as untracked, including a pre-existing `.serena/` directory; leave unfamiliar files alone. The app is named ProtoLaunch with ID `me.foxtails.lakehead`. `app/src/main/AndroidManifest.xml` declares only a normal LAUNCHER activity; `MainActivity` displays `feature/home/HomePlaceholder.kt`. `:core:model`, `:core:grid`, `:core:designsystem` and `:platform:launcherapps` have Gradle configurations but no implementation source yet. This is not a functional HOME launcher.

## Completed and decisions

- Replaced the one-line `AGENTS.md` with project-specific scope, module/state ownership, launcher invariants, phase gates, validation, Git discipline and long-horizon handoff protocol.
- Converted `docs/agents.md` into a pointer rather than a conflicting second rulebook. Added `state-logs/README.md` and the durable 0B task spec; `README.md` now points to the state-log entry point.
- This log is the first handoff. The next implementation request is deliberately smaller than the 0B exit gate: HOME eligibility and role request wiring only, owned by `:app`. Installed-app discovery, launching, callbacks and grid implementation are later separate requests.

## Validation

- This documentation slice: `python3 tools/scripts/module_graph.py` — PASS; `python3 tools/scripts/repo_map.py` — PASS; a Python link-existence check across the seven new/updated guidance files — PASS. Inspected the manifest, activity and home placeholder directly. No Gradle build/test/lint was run for the docs-only slice.
- Earlier foundation validation, recorded in `docs/agents/tasks/foundation.md`: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug test lint` — PASS at foundation creation. `test` had NO-SOURCE modules; this is not evidence for launcher behavior.
- Device validation — NOT RUN; earlier `adb devices` showed no attached device. HOME role, profile inventory, launches and package updates remain unverified and unimplemented.

## Remaining and next action

Next agent: read `AGENTS.md`, `state-logs/CURRENT.md`, this handoff and `docs/agents/tasks/0b-launcher-platform.md`. Inspect `app/src/main/AndroidManifest.xml` and `MainActivity.kt`. **One task only:** make ProtoLaunch eligible as HOME and provide a focused user-triggered default-launcher role request on supported APIs, keeping role/platform mechanics separate from UI presentation. Do not add the installed-app catalogue, grid, search, folders or persistence in the same request. Exit condition: merged manifest proves HOME eligibility; a device test demonstrates the request/selection if a device is available; build, test, lint and audits pass; otherwise log the precise unverified device behavior. Create/update the next session log and CURRENT pointer before stopping.
