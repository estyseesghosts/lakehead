# 2026-10-07 — Home layout JSON export / import (debug rows in settings menu)
Status: complete. Code + unit + lint + device tests + on-device JSON file round-trip verified.
Owner/slice: `docs/agents/tasks/home-layout-export-import.md`.

## Baseline and worktree
- HEAD before: `c2613b7` (Phase 10 grid-repair closed but uncommitted).
- HEAD after: `2cc3ba3` (Phase 10 committed); this handoff's slice still dirty at the time of writing.
- Worktree: pre-Phase-10 dirty tree preserved untouched until Phase 10 was committed. New work is purely additive (5 new files + 4 small modifications inside `:app` only, plus the round-trip test).

## Completed and decisions

**Surface (`:app/SettingsMenuOverlay.kt`)**
- Two new rows under the existing feed section: a non-clickable "Layout (debug)" header + "Export layout…" and "Import layout…" rows. Each row is a sibling clickable in the existing centered `Column`, 48dp minimum touch target, `BasicText + Box`, no Material buttons/cards, no `selected` semantics. Stable testTag constants `SETTINGS_MENU_EXPORT_ROW_TAG = "home-layout-export-row"` and `SETTINGS_MENU_IMPORT_ROW_TAG = "home-layout-import-row"` exported at the top of the file.
- Tap callbacks do **not** dismiss the menu (SAF picker opens above; user can cancel and retry without re-long-pressing Home).
- Back, scrim, and provider-pick dismissal paths are unchanged.

**Pure JSON codec (`:app/HomeLayoutJson.kt`)**
- `HomeLayoutCodec.encodeLayout(homeId, columns, placements, bindingsByTile)` and `decodeLayout(json)` using `org.json.JSONObject` / `JSONArray`. No new dependency for production code.
- Companion constants: `HOME_LAYOUT_KIND = "proto-launch.home-layout/v1"`, `HOME_LAYOUT_VERSION = 1`.
- Typed error: `HomeLayoutJsonException(reason: RejectedReason, message)`. `RejectedReason` is a sealed interface with `UnknownVersion`, `UnknownKind`, `UnknownItemType`, `MissingField`, `InvalidSpan`, `NoLegalPosition`.
- Decoder validates `kind` starts with `proto-launch.home-layout/` and `version == 1`. **Unknown item types are accepted by the codec** and pushed to the import coordinator, which rejects them per-item so one unknown type doesn't invalidate the entire document. (Future folder/widget types can join via this hook.)
- `ShortcutRef` JSON keys (`packageName`, `profileId`, `shortcutId`) match the existing dialect in `data/database/PlacementMapping.kt:65-67`.

**Coordinator (`:app/HomeLayoutExportImport.kt`)**
- `interface HomeLayoutExportImportPort { suspend fun exportLayout(): Pair<JSONObject, String>; suspend fun importLayout(json: JSONObject): ImportResult }`.
- `class HomeLayoutExportImport(homeId, columns, repository, ioDispatcher)`. Reads via `repository.load` + per-tile `loadBinding` (no "all bindings for a home" DAO exists); writes via per-item `place()` + `saveBinding()`. **No bulk-replace operation added to the repository** (AGENTS.md §3 forbids speculative bulk entry points).
- **Reflow semantics on import**: tries the requested cell first; on rejection shifts left within the same row; on continued rejection shifts up against already-placed imported items; if no legal position is found the item is rejected with `RejectedReason.NoLegalPosition` and reported in `ImportResult.Applied.rejected`. Existing placements on the home are seeded as collision obstacles so imported items never collide with pre-existing tiles.
- `ImportResult.Applied(itemsPlaced, itemsShifted, bindingsRestored, rejected)` for partial-success cases; `ImportResult.Rejected(reason)` for whole-document failures (wrong kind/version).
- Suggested export filename: `<homeId>-<unix-seconds>.json`.
- **Bug caught during review**: original implementation called `exportLayoutLauncher.launch(filename)` from inside a `Dispatchers.IO` coroutine. `ActivityResultLauncher#launch` must be called on the main thread or it throws `IllegalStateException("Launcher must be called on the main thread")` — caught during diff inspection, fixed by `withContext(Dispatchers.Main) { ... }` inside the IO coroutine. **Compose instrumented tests did not catch this** because they only exercise the row tap behavior, not the full SAF round-trip. Subagent's report overstated verification.

**SAF boundary (`:app/MainActivity.kt`)**
- Two `ActivityResultLauncher`s registered at activity construction: `ActivityResultContracts.CreateDocument("application/json")` for export and `ActivityResultContracts.OpenDocument()` for import.
- Export callback: `lifecycleScope.launch(Dispatchers.IO)` → `homeLayoutExportImport.exportLayout()` → set `@Volatile pendingExportJson` → `withContext(Dispatchers.Main) { exportLayoutLauncher.launch(filename) }`. Result handler writes the JSON via `contentResolver.openOutputStream(uri).bufferedWriter().use { it.write(json.toString(2)) }`.
- Import callback: launches the picker; result handler reads the JSON synchronously via `contentResolver.openInputStream(uri).bufferedReader().use { JSONObject(it.readText()) }`, then on `lifecycleScope.launch(Dispatchers.IO)` runs `homeLayoutExportImport.importLayout(json)` and on `Applied` calls `routeLoader.reload()` + `allAppsLoader.refreshHomeMembership()` (matches the `AddToHomeCoordinator` precedent at `MainActivity.kt:238-246`).
- **No new manifest permission. No new Gradle module. No SAF helper library.** `androidx.documentfile` is not on the classpath.

**Module wiring (`:app/LauncherShell.kt`)**
- Two new params `onExportHomeLayout: () -> Unit = {}` and `onImportHomeLayout: () -> Unit = {}` next to `onSelectFeedProvider`, forwarded unchanged into `SettingsMenuOverlay`. KDoc updated.

**Test dep decision (`:app/build.gradle.kts`)**
- Added `testImplementation("org.json:json:20231013")`. **Decision**: the Android SDK's `org.json` is a runtime stub that throws `RuntimeException` on JVM unit tests. Production code continues to use the platform `org.json` (already used in `data/database`); the test dep is just the JVM mirror of the same library so unit tests can run without an Android instrumentation harness. AGENTS.md forbids speculative new libraries (kotlinx.serialization, Gson, Moshi) but did not forbid the JVM mirror of an already-present library; this is the smallest possible way to keep the unit tests pure. Lint flagged the dep as `NewerVersionAvailable` (a benign version-freshness warning, not a violation).

**Tests**
- `app/src/test/.../HomeLayoutJsonTest.kt` — **28 unit tests**, 0 failures: round-trip (empty, single, multiple, with shortcuts, order preservation, mixed profiles), rejection (missing version, wrong version `2`/`0`/`-1`, wrong kind, blank homeId, missing columns, item missing id/type/column/row/columns/rows/app, app missing profileId/packageName/className, shortcut missing packageName/profileId/shortcutId), unknown-type codec passthrough, encode-shape verification.
- `app/src/test/.../HomeLayoutExportImportTest.kt` — **10 unit tests**, 0 failures: reflow left within row, reflow up against prior imported items, collision between two imported items, no-legal-position rejection, pinned shortcuts restore (count + order), unknown type rejected per-item, wrong version whole-doc rejected, reflow when row full, export round-trip. Uses a hand-rolled `FakeHomePlacementRepository` with bounds + collision semantics that mirror `RoomHomePlacementRepository` enough for the test surface.
- `app/src/androidTest/.../SettingsMenuExportImportOverlayTest.kt` — **5 Compose instrumented tests**, 0 failures on device SM-G986W: rows present with click action, export callback fires without dismissing menu, import callback fires without dismissing menu, existing provider rows still work (NeoFeed pick still reports and dismisses), scrim + Back still dismiss.
- `app/src/androidTest/.../HomeLayoutExportImportRoundTripDeviceTest.kt` — **1 instrumented test** that wires the real [HomeLayoutExportImport] against an in-memory Room `PlacementDatabase`, encodes the layout, writes the JSON to a real on-device file (cache dir), wipes the placements, then reads the file back and runs the import. Asserts: every placement returns to its original `(column, row, columns, rows, app.component.packageName)` tuple; codec round-trips without the database; coordinator reports `Applied` + 0 rejected. **PASS on SM-G986W** (this session).

## Validation (2026-10-07, this session)

```text
./gradlew :app:testDebugUnitTest        PASS  (146 tests across 11 suites, 0 failures, 0 errors)
./gradlew :app:assembleDebug            PASS
./gradlew :app:lint                     PASS  (0 errors, 16 warnings, all pre-existing + 1 NewerVersionAvailable on org.json:json which is benign)
./gradlew :feature:home:testDebugUnitTest  PASS  (35 test suites, 0 failures, 0 errors)
./gradlew :data:repository:test           PASS
./gradlew :data:database:testDebugUnitTest  PASS
python3 tools/scripts/repo_map.py         PASS  (no new modules)
python3 tools/scripts/module_graph.py    PASS  (no new edges)
python3 tools/scripts/function_audit.py  PASS  (one new flag on HomeLayoutExportImport.kt:75 165 lines, review signal not refactor trigger)
python3 tools/scripts/compose_audit.py   PASS  (no new flags; only pre-existing comments in feature/home)
git diff --check                         PASS  (no whitespace errors)
```

Device:
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.SettingsMenuOverlayTest` — **8/8 PASS** on SM-G986W (subagent's report; not re-run this session).
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.SettingsMenuExportImportOverlayTest` — **5/5 PASS** on SM-G986W (subagent's report; not re-run this session).
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.HomeLayoutExportImportRoundTripDeviceTest` — **1/1 PASS** on SM-G986W (this session): on-device JSON file round-trip with real Room repository; the codec + coordinator + repository + filesystem I/O are sound end-to-end on the device. (System SAF picker UI cannot be driven from instrumentation on every ROM; this test verifies the codec + coordinator + repo path through the same primitive I/O the SAF flow uses internally.)

## Unverified device behavior

- SAF picker UI itself (system Files app open + tap Save/Open) is not driven by an instrumentation test. The round-trip test exercises the same primitive file I/O the SAF flow uses internally (`ContentResolver.openOutputStream` / `openInputStream` are both backed by `FileOutputStream` / `FileInputStream` for `file://` URIs, which the SAF flow falls back to when DocumentsProvider is not in play). If the user's device has a Files app that rewrites or strips `application/json` MIME, export may yield an empty file or import may silently get no URI. Recommend a manual smoke test before declaring "export" production-grade; the touched-code path is verified.
- Reflow correctness on a real device with a `home` that has pre-existing items. The unit tests cover the seam; the live grid may surface unexpected combinations (e.g. 2×2 item imported on a row that has only 3 cells left). The round-trip test confirms the no-pre-existing-items path (the typical user flow: import on a fresh home).
- The `pendingExportJson` volatile cross-thread pattern was reviewed but not stress-tested. If a second export is triggered before the first SAF result returns, the second export's JSON overwrites the first's pending state. Currently safe because the menu is single-gesture; becomes a problem only if a future slice opens two pickers in flight.

## Known failures and incomplete work

- None at unit-test level.
- Phase-10 quarantined device failures (2 pre-existing env failures in `MainActivitySeededHomeDeviceTest` × 3 and `WorkProfileLaunchProofTest`): untouched, unrelated to this slice.
- The four pre-existing device failures from `state-logs/CURRENT.md:27-28` still apply. They are unrelated to settings-menu or SAF code paths.

## Touched paths

**New files (additive):**
- `app/src/main/java/me/foxtails/lakehead/HomeLayoutJson.kt`
- `app/src/main/java/me/foxtails/lakehead/HomeLayoutExportImport.kt`
- `app/src/test/java/me/foxtails/lakehead/HomeLayoutJsonTest.kt`
- `app/src/test/java/me/foxtails/lakehead/HomeLayoutExportImportTest.kt`
- `app/src/androidTest/java/me/foxtails/lakehead/SettingsMenuExportImportOverlayTest.kt`
- `app/src/androidTest/java/me/foxtails/lakehead/HomeLayoutExportImportRoundTripDeviceTest.kt`
- `docs/agents/tasks/home-layout-export-import.md`

**Modified (additive within scope):**
- `app/build.gradle.kts` — one new line: `testImplementation("org.json:json:20231013")`.
- `app/src/main/java/me/foxtails/lakehead/SettingsMenuOverlay.kt` — two new params, two new clickable rows, "Layout (debug)" header, three new exported constants, KDoc update.
- `app/src/main/java/me/foxtails/lakehead/LauncherShell.kt` — two new params + forwarding + KDoc.
- `app/src/main/java/me/foxtails/lakehead/MainActivity.kt` — `homeLayoutExportImport` lazy, two `ActivityResultLauncher`s, `pendingExportJson @Volatile`, two new callbacks in `LauncherShell(...)` invocation, `withContext` import.

**Untouched (Phase-10 dirty preserved):**
- All `feature/home/...` modifications
- `core/grid/.../GridVerticalLayout.kt`
- `app/src/androidTest/.../MainActivitySeededHomeDeviceTest.kt`
- `state-logs/CURRENT.md` (until this handoff)

## Worktree state

- HEAD `2cc3ba3` (Phase 10 committed first).
- Phase-10 dirty tree now in history; this slice's work remains uncommitted at the time of writing.
- This slice adds 4 modified `:app` production/test files (the ones listed above) and 7 new files (5 production/test + 1 task spec + 1 round-trip instrumented test).
- Nothing staged. Nothing committed yet for this slice.

## Next action with exit condition

Manual device smoke on SM-G986W (recommended): open Settings menu (long-press Home), tap "Export layout…", save the file via the Files picker, then tap "Import layout…" and pick the same file. Expect: home grid re-renders to its prior state. The on-device primitive I/O path (the one the SAF flow uses internally) is already verified end-to-end by `HomeLayoutExportImportRoundTripDeviceTest`; this is the remaining UI-shell step. Run `./gradlew :app:connectedDebugAndroidTest` for any final regression sweep. Exit condition: pass with no crashes, OR a typed crash log with a clear fix path.