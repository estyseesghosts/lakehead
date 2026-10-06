# 2026-09-27 — A6 shortcut gateway (platform + model + app seam)
Status: completed (code + unit validation; live shortcut UI display is B1's slice)
Owner/slice: `:platform:shortcuts` + `:core:model` + `:app` seam, `docs/agents/tasks/01.md` slice A6

## Baseline and worktree
- Baseline: `state-logs/CURRENT.md` points at the two-axis drag fix (CODE-COMPLETE, DEVICE-PENDING); worktree had unrelated concurrent-agent edits (agent-file modifications, `docs/01.md` + `docs/agents/tasks/01.md` added, `ProfileDisplay.kt` from the parallel A4 sorting slice). No commits made; all changes uncommitted.
- No `platform/shortcuts` module existed; no `ShortcutManager`/`getShortcuts`/`startShortcut` usage anywhere. The `surfaceEntries`/`onSurfaceExecute` seam in `LauncherShell`/`HomeCanvas`/`EditShortcutMenu` already exists and defaults to empty (B1 wires it).

## Completed and decisions
- New `:platform:shortcuts` module (in `settings.gradle.kts`; `build.gradle.kts` mirrors launcherapps with `api(:core:model)`):
  - `ShortcutGateway.kt` — public domain-facing contract: `StateFlow<Map<AppIdentity, List<LauncherShortcut>>>`, `shortcutsFor`, `refresh`, `launch`, `start`/`stop`.
  - `LauncherAppsShortcutGateway.kt` — app-context gateway, per-`AppIdentity` bounded cache, query-on-select via `LauncherApps.ShortcutQuery` with `FLAG_MATCH_MANIFEST or FLAG_MATCH_DYNAMIC` scoped by `setPackage`, rank-ordered publish, `startShortcut` with own profile-token mapping (mirrors launcherapps' internal adapter; no platform→platform impl dependency), `ActivityNotFound/Security/IllegalArgument/IllegalState → false`. Query failures keep last-good; only successful queries publish (even empty).
  - `ShortcutQueryMapper.kt` — pure row→domain mapping (short label → long label → id; rank sort with id tie-break; blank-id drop; dup-id keeps lowest rank; negative rank clamps). Own file for testability.
  - `ShortcutSnapshotCache.kt` — internal access-ordered bounded (64) per-identity cache with immutable snapshots, `identitiesForPackage`/`removePackage`.
  - `ShortcutInvalidationSource.kt` — `LauncherApps.Callback`: `onShortcutsChanged` → requery package+profile; package changed → coarse requery of cached identities; package removed → drop package entries. `onPackageAdded`/`onPackagesAvailable/Unavailable` are required-abstract no-ops (new/availability-only packages have no cached entries to refresh).
- `core/model/LauncherShortcut.kt` — `ref,label,rank` (label nonblank, rank ≥ 0; no icon this slice).
- `:app` `ShortcutSurfaceLoader.kt` — `entriesForTile(tileId, identity)`: gateway rank order + `repo.loadBinding` → `ShortcutSurfaceEntry(id,label,isPinned∈binding,isPinEnabled=true)`; empty shortcuts → empty list without touching the repo (existing `editShortcutMenuEntries` empty-hides). `execute(ref)` → `gateway.launch` off-main. Pin toggle inert: `isPinEnabled=true` display, caller leaves `onSurfaceTogglePin` unwired (B2 owns capacity gating).
- `:app` wiring only: `implementation(project(":platform:shortcuts"))`; `MainActivity` lazy `shortcutGateway` + `shortcutSurfaceLoader`, `start`/`stop` in `onStart`/`onStop`. No signature changes to `HomeCanvas`/`Grid`/`LauncherShell`/`EditShortcutMenu`; manifest unchanged; no new permissions.
- API correction found by building: `LauncherApps.getShortcuts` takes `(ShortcutQuery, UserHandle)` (not `(flags, package)`), `onShortcutsChanged` carries `(packageName, List<ShortcutInfo>, user)`, and `LauncherApps.Callback` has abstract `onPackageAdded/onPackagesAvailable/onPackagesUnavailable`. Flags must be `ShortcutQuery.FLAG_MATCH_*` (lint WrongConstant), not `ShortcutManager.*`.
- Dependency direction verified: `:platform:shortcuts → :core:model` only; `:app → :platform:shortcuts`; no feature→platform or platform→platform edges.

## Validation
- `./gradlew :platform:shortcuts:assembleDebug :platform:shortcuts:testDebugUnitTest` — SUCCESS (11 tests: mapper 6, cache 5).
- `./gradlew :core:model:testDebugUnitTest :app:testDebugUnitTest` — SUCCESS (includes new `LauncherShortcutTest` 5 tests, `ShortcutSurfaceLoaderTest` 4 tests; one initial failure fixed: test fake now honors the gateway's rank-order contract).
- `./gradlew assembleDebug` — SUCCESS.
- `./gradlew lint` — SUCCESS all modules (one real finding fixed: WrongConstant on shortcut flags; remaining warnings mirror pre-existing dependency-version notices).
- `./gradlew test` — SUCCESS, no regressions.
- `python3 tools/scripts/repo_map.py`, `module_graph.py`, `function_audit.py`, `compose_audit.py` — ran; new module/edges present; audits flag only pre-existing items, none in new files.
- `./gradlew installDebug` — SUCCESS, installed `app-debug.apk` (+ fixture) on SM-G986W Android 13 (device R5CN70E2SGV).
- Device smoke: `adb shell am start -n me.foxtails.lakehead/.MainActivity` launches; process alive (pid 6392); logcat shows no exception/fatal/crash for the process — gateway `start` (callback registration) runs clean.
- NOT-RUN on device: real rank-ordered shortcut display, profile-correct launch, zero-shortcut hiding, live publish update, work-profile shortcuts — all require the B1 menu wiring (surface entries still default to empty in production), plus 2+ 3P shortcut-publishing apps on the device.

## Remaining and next action
- B1 (`five`) wires `ShortcutSurfaceLoader.entriesForTile(selectedTile)` → `LauncherShell surfaceEntries` and `onSurfaceExecute` → `loader.execute` off-main; needs the selected-tile→`AppIdentity` projection plus `gateway.refresh(identity)` on selection change (query-on-select) without re-entering edit on tap-switch.
- Suggested device tests at B1 gate: 2+ 3P apps with shortcuts, work-profile app, zero-shortcut app, live shortcut publish while menu open, pre-HOME-role note.
- Next concrete action: B1 owner implements the selected-tile projection + loader wiring; exit when long-press shows live rank-ordered rows and row tap executes under the correct profile.
