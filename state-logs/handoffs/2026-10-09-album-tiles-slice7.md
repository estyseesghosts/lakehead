# 2026-10-09 — Album tiles slice 7 ("Change album" / "Remove album" + decision 7)

Status: slice 7 complete, committed. Next: slice 8 (exit check).
Owner/slice: album-tiles agent; `docs/agents/tasks/album-tiles.md` slice 7.

## Baseline and worktree

HEAD `bb6997b` on `main`, clean tree at session start (`git status --short` empty).
Device `R5CN70E2SGV` (model `SM-G986W`, API 33) connected via adb throughout.

## Completed

- Edit-menu album rows through the existing path (no second menu system):
  - `ShortcutSurfaceEntry` (`:core:designsystem` `ShortcutSurfaceRow.kt`) gains
    `isAction: Boolean = false`. Action rows render label-only with no pin
    toggle; taps report the same `onSurfaceExecute` by row id.
  - `EditShortcutMenu` (`:feature:home`) renders `isAction` rows with a private
    `MenuActionRow` (same fixed row height, tags, label style/semantics as
    `ShortcutSurfaceRow` minus the pin box) and documents the contract in its
    header KDoc. Size/visibility math untouched: action rows count as rows, and
    a bound tile with zero shortcuts now yields a menu instead of empty-hide.
  - New `:app` `AlbumMenuRows.kt`: `ALBUM_MENU_CHANGE_ID/REMOVE_ID`
    (`protolaunch:album:change/remove`, namespaced against platform shortcut
    ids), labels "Change album" / "Remove album", `albumMenuRows(binding)`
    builder (null binding → no rows; else Change then Remove after shortcuts).
  - `ShortcutSurfaceLoader.entriesForTile` appends `albumMenuRows` whenever an
    album binding exists — even with zero shortcuts. The shortcut-binding
    read is still skipped when there are no shortcuts; the album binding is
    always read. Pin toggles can never fire for action rows (none rendered);
    `togglePin` on an album id resolves to no shortcut and returns false.
- Coordinator entry points (`AlbumTileCoordinator`):
  - `openAlbumPicker(tileId)` reuses the pending-tile flow: access granted →
    `openPicker()`; denied → `onRequestPermission()` (the permission launcher
    in `MainActivity`), continued by the existing `onPermissionResult`. This
    is the only place a denied permission is re-requested. Unknown tiles ignored.
  - `suspend removeAlbumBinding(tileId)` deletes the row, drops in-memory
    binding/tap count, closes a pending picker for the same tile, and
    reprojects (tile renders its icon; tile itself untouched — tile removal
    still cascades separately). Suspend so the caller can refresh the menu
    after the delete completes (no stale-rows race).
- `ShortcutMenuCoordinator.refreshSelection()` re-reads the open menu with no
  selection/tile transition (Remove path). `execute` needs no change: album
  ids match no gateway shortcut and return false.
- `MainActivity` routes `onSurfaceExecute` by id (Change → `openAlbumPicker`,
  Remove → `removeAlbumBinding` + `refreshSelection`, else shortcut execute)
  and wires `loadDeviceAlbumBuckets = { mediaGateway.albums().map(bucketId).toSet() }`.
- Decision 7 implemented in this slice (no carry-over): `HomeLayoutJsonItem`
  gains optional `album: HomeLayoutJsonAlbumRef?` (bucket id + name only);
  `encodeLayout` takes defaulted `albumBindingsByTile`; `decodeItem` accepts a
  missing `album` as null and rejects a malformed one (`MissingField`, whole
  document — consistent with shortcuts). `HomeLayoutExportImport` exports
  album bindings and, on import, restores a binding only when its bucket is in
  the device set (queried once per import, only when an item carries one;
  denied permission reads as empty); otherwise the binding drops silently
  while the tile is still placed (no rejection, no counter).

## Tests

- Unit (all 0 failures): `AlbumTileCoordinatorTest` 24/24 (6 new: change
  opens picker + pick rebinds, denied→grant opens, deny keeps closed + binding
  kept, unknown tile ignored, remove clears binding/photo/taps, remove closes
  pending picker); `ShortcutSurfaceLoaderTest` 11/11 (3 new + 1 renamed:
  zero-shortcut bound tile yields only album rows with shortcut binding
  unread, rows appended after shortcuts, album-id togglePin false/no-write);
  `ShortcutMenuCoordinatorTest` 22/22 (3 new: zero-shortcut bound menu shows
  action rows, `refreshSelection` drops rows after binding removal, album-id
  execute false/no-launch); `HomeLayoutJsonTest` 31/31 (3 new: album
  round-trip, absent → null, malformed → throws);
  `HomeLayoutExportImportTest` 13/13 (3 new: import restores when present,
  drops-but-places when absent, export includes album).
- Device: `:feature:home` suite 63/63 on R5CN70E2SGV (61 prior + 2 new
  `EditShortcutMenuComposeTest` action-row tests: no pin toggle + execute by
  row id; action-only menu shows with zero shortcuts).
- New `:app` `AlbumMenuDeviceTest` 4/4 via
  `adb shell am instrument -e class …` against real in-memory Room:
  zero-shortcut bound menu rows, change rebinds the real row, denied requests
  permission and opens nothing (binding kept), remove deletes the real row +
  clears photo state + refreshed menu drops rows. (Targeted run chosen
  deliberately: the full `:app` suite has the pre-existing themeless-shell
  crash that aborts later classes.)

## Validation (exact commands + outcomes)

- `./gradlew :app:testDebugUnitTest` → PASS (`:app` 190/0 incl. rerun after
  final edit); `./gradlew test` → PASS (full unit 1577/0 = 1559 + 18 new).
- `./gradlew assembleDebug` → PASS; `./gradlew installDebug` → PASS.
- `./gradlew lint` → PASS (re-run after all files incl. new device test added).
- `python3 tools/scripts/repo_map.py`, `module_graph.py` → no new module
  edges (`:app → :platform:media` from slice 6 unchanged).
- `function_audit.py` / `compose_audit.py` → no flags on new code
  (remaining hits pre-existing).
- `:feature:home:compileDebugAndroidTestKotlin`, `:app:compileDebugAndroidTestKotlin`
  → PASS (CI never compiles androidTest).
- Device: `:feature:home:connectedDebugAndroidTest` 63/63;
  `AlbumMenuDeviceTest` 4/4; `adb` preconditions: `com.sec.android.gallery3d`
  present on user 0 (offer predicate matches on-device Gallery), HOME role
  currently unset (user selects ProtoLaunch for manual passes).

## Device-run incident (environmental, resolved)

- First `:feature:home` device run: BUILD FAILED after ~12 min, 54/63 with
  `IllegalStateException: No compose hierarchies found`; every
  composition-requiring test failed and every non-composition test passed.
  Cause: the phone was Dozing (`dumpsys power` → `mWakefulness=Dozing`).
  Fix: `adb shell input keyevent KEYCODE_WAKEUP` + `adb shell svc power
  stayon true` for the run; re-run 63/63 green in 1m29s; `stayon false`
  restored afterwards.
- `connectedDebugAndroidTest` on this AGP rejects `--tests`; class-targeted
  runs go through `adb shell am instrument -e class …` instead.
- `adb shell pm list packages` without `--user 0` fails: shell may not query
  user 150 (work profile). Use `--user 0`.

## Unverified on device (slice 8)

Finger-driven E2E: resize gesture on a real gallery tile → prompt → system
permission dialog → picker scroll/pick → photo on Home; edit-menu Change /
Remove taps by hand; package add/remove without restart; 15-minute rotation
timing (unit-proven with fake clock only); partial access on PLG110
(deferred to the user's pass per spec — never claimed).

## Touched paths (slice 7 commit)

- `core/designsystem/.../ShortcutSurfaceRow.kt` (isAction flag)
- `feature/home/.../EditShortcutMenu.kt` (MenuActionRow + branch + KDoc)
- `feature/home/.../EditShortcutMenuComposeTest.kt` (2 tests)
- `app/.../AlbumMenuRows.kt` (new), `AlbumTileCoordinator.kt`,
  `ShortcutMenuCoordinator.kt`, `ShortcutSurfaceLoader.kt`,
  `MainActivity.kt`, `HomeLayoutJson.kt`, `HomeLayoutExportImport.kt`
- `app/.../AlbumTileCoordinatorTest.kt`, `ShortcutSurfaceLoaderTest.kt`,
  `ShortcutMenuCoordinatorTest.kt`, `HomeLayoutJsonTest.kt`,
  `HomeLayoutExportImportTest.kt`
- `app/.../AlbumMenuDeviceTest.kt` (new androidTest)
- `state-logs/handoffs/2026-10-09-album-tiles-slice7.md` (this file),
  `state-logs/CURRENT.md` (pointer)

## Next action (slice 8, exit condition)

Run the full `AGENTS.md` §6 validation yourself, then the device pass on
R5CN70E2SGV per the slice 8 exit (install debug; resize a gallery3d-segment
gallery tile to ≥2×2; accept; grant; pick an album; verify photo render,
tap advance, change/remove from the edit menu, package add/remove without
restart — rotation timing stays unit-proven, partial access stays deferred),
write the closing handoff, update `CURRENT.md`, commit separately. Exit
condition: truthful handoff with exact commands + outcomes and device
evidence (or explicit not-run statements).
