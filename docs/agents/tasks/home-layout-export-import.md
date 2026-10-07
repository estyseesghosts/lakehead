# Home layout JSON export / import (debug)

Status: planned
Phase: post-1C persisted home (debug-only surface inside the existing transient settings menu)
Owner: `:app` wiring + `:data:repository` consumer; `:core:model` unchanged

## Objective and boundary

Add two rows to the existing `SettingsMenuOverlay` (opened by long-pressing the Home pill) so a user can **export** the current home layout as a JSON file and **import** one back. This is a debug surface for development and test launches; it is not a production backup feature, sync, or document management platform. No auto-prompting, no cross-home UI, no new permission.

The two rows report callbacks to `:app`; the menu owns no catalogue, persistence, or file-IO logic. SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) lives behind a domain-facing port in `:app` so a future `:platform:documents` module can replace it without touching `:feature:*` or `:core:*`. Both rows are visible at all times — the menu does not auto-dismiss on tap; the SAF picker opens on top and the user can cancel and try again without re-long-pressing Home.

## Scope

In scope:

- Two new rows under the existing feed-provider rows in `SettingsMenuOverlay`: "Export layout…" and "Import layout…", 48dp touch target each, `BasicText + Box` (no Material buttons/cards), `Role.Button`, stable `testTag` `"home-layout-export-row"` and `"home-layout-import-row"`. A non-clickable "Layout (debug)" header sits above them, color `onSurfaceVariant`, mirroring the existing feed header.
- A pure, framework-free JSON codec in `:app` that round-trips the persisted layout (one home) including per-tile pinned shortcuts. The codec does not import `org.json` into `:core:model`; it lives in `:app` and takes framework-free models in and returns `org.json.JSONObject`/`JSONArray` out.
- A `HomeLayoutExportImportPort` interface in `:app` with two suspend operations (`exportLayout()` returning a `JSONObject` + suggested filename, `importLayout(json)` returning an `ImportResult`).
- A `HomeLayoutExportImport` implementation that owns the repository reads/writes and the SAF `ActivityResultLauncher`s; `MainActivity` registers the launchers and forwards two new `LauncherShell` callbacks (`onExportHomeLayout`, `onImportHomeLayout`).
- 5-column grid (matches production wiring at `MainActivity.kt:154`).
- On import: **reflow** an item whose logical span no longer fits the current column count, or whose requested cell collides with another imported item, so the user never silently loses tiles. Reject only items that fail the grid in a way that cannot be reflowed (e.g. item type is `null`/`unknown`; `null` app fields; `null` profile/component).
- Pure-helper unit tests for the JSON codec; Compose instrumented tests for the new menu rows (no `selected` semantics, correct `testTag`s, tap reports callback without dismissing the menu, scrim/back still dismiss, provider rows still work).

Out of scope:

- Folder / widget item types (per D1 deferred).
- Cross-home import UI (this slice hard-targets `PRODUCTION_HOME_ID = HomeId("home-main")`).
- Cloud sync, periodic export, automatic backups, manifest permissions.
- A new `:platform:documents` Gradle module. AGENTS.md says modules appear when real code needs the boundary, not as an empty directory exercise. A single `:app` coordinator with a port interface is the right granularity for two buttons.
- Re-rendering the home after import via the loader's optimistic apply. The import writes directly through the repository, then `routeLoader.reload()` is called from `MainActivity` (matches the `AddToHomeCoordinator` precedent at `MainActivity.kt:238-246`).
- Removing the existing transient `isSettingsOpen` hoist.

## JSON contract

```json
{
  "version": 1,
  "kind": "proto-launch.home-layout/v1",
  "exportedAtMillis": 1730000000000,
  "home": {
    "id": "home-main",
    "columns": 5,
    "items": [
      {
        "id": "tile-uuid-1",
        "type": "APP",
        "column": 3,
        "row": 0,
        "columns": 1,
        "rows": 1,
        "app": {
          "profileId": "0",
          "packageName": "com.example",
          "className": "com.example.MainActivity"
        },
        "pinnedShortcuts": [
          { "packageName": "com.example", "profileId": "0", "shortcutId": "sc-1" }
        ]
      }
    ]
  }
}
```

Rules:

- `version` is the integer format version (currently `1`). `"proto-launch.home-layout/v1"` is the string `kind`. Import rejects any document whose `kind` does not start with `proto-launch.home-layout/` or whose `version > 1`.
- `home.id` is recorded but ignored on import (this slice always targets `PRODUCTION_HOME_ID`). A future cross-home slice can warn on mismatch.
- `home.columns` is recorded; import ignores it (we reflow to the current column bound).
- `items[].type` is the only place a future folder/widget item type can land without breaking v1 readers (v1 reads unknown types as a per-item rejection, not a domain error). This slice writes only `APP`.
- `pinnedShortcuts[]` order is preserved (matches `findShortcuts preserved insertion order is documented in TileBinding`).
- Field names for `ShortcutRef` (`packageName`, `profileId`, `shortcutId`) are the same as `data/database/PlacementMapping.kt:65-67` — one canonical dialect across the repo.
- All fields are required (no default values). Import rejects documents with missing required fields, blank strings, negative `column`/`row`, non-positive `columns`/`rows`, or `null` app fields with a clear typed reason.

## Ownership and dependency boundaries

- `:core:model` is unchanged. No `org.json` enters `:core:model`; the codec lives in `:app`.
- `:data:repository` is unchanged. Import drives `quantity + 1 repository.place(...)` per accepted item and `quantity` `saveBinding(...)` per binding (one binding per placement that has non-empty `pinnedShortcuts`). No new bulk-replace operation; AGENTS.md §3 forbids speculative bulk entry points.
- `:feature:home` is unchanged. No new menu composition outside `SettingsMenuOverlay`.
- `:app` wires the port to the SAF contract (one `ActivityResultContracts.CreateDocument("application/json")` launcher, one `ActivityResultContracts.OpenDocument(arrayOf("application/json"))` launcher). `MainActivity` registers them and forwards two new `LauncherShell` callbacks.
- `LauncherShell` gains two new params: `onExportHomeLayout: () -> Unit = {}` and `onImportHomeLayout: () -> Unit = {}`. It forwards them into `SettingsMenuOverlay` unchanged. The transient `isSettingsOpen` hoist is unchanged.
- The new rows in `SettingsMenuOverlay` live in the existing centered `Column` after the feed provider rows and before the footer (or replaces no current footer for `FeedAvailability.Available`). The body `desiredHeightPx = 280.dp` (`SettingsMenuOverlay.kt:143`) is large enough — header + 2 provider rows + new "Layout (debug)" header + 2 new rows + optional footer still fits comfortably.

## Reflow semantics on import

If a JSON item's `column + columns` exceeds the current grid's column bound, the import attempts to **shift left** to the rightmost legal origin in the same row whose row's prior imported items don't collide. If that fails (no legal origin in that row), the import shifts the item **up** (to the smallest row whose prior imported items don't collide). If that still fails, the item is rejected with `RejectedReason.NO_LEGAL_POSITION` and reported in the import result. This preserves every other tile and never silently drops data.

Collisions between two imported items are resolved by processing them in JSON order; later items are shifted as above against the already-placed earlier items. Per-item status (`Placed` / `Shifted` / `Rejected`) is returned in `ImportResult` so the caller can show the user which tiles landed where.

## Acceptance criteria

- The transient settings menu shows a "Layout (debug)" header, then an "Export layout…" row with `testTag = "home-layout-export-row"`, then an "Import layout…" row with `testTag = "home-layout-import-row"`. Both rows have `Role.Button` and 48dp minimum touch target. Neither row carries the `selected` semantics flag.
- Tapping "Export layout…" invokes `onExportHomeLayout` without dismissing the menu; the menu stays open. `MainActivity`'s launcher opens a system file picker with MIME `application/json` and a suggested filename `<home-id>-<unix-seconds>.json`. The user picks a destination; the launcher reads `result.data?.data` and writes the JSON via `ContentResolver`. Cancellation closes the picker; the menu stays open.
- Tapping "Import layout…" invokes `onImportHomeLayout` without dismissing the menu; the launcher opens a system file picker filtered to MIME `application/json`. The user picks a file; the launcher reads the JSON, runs the import through the port, then calls `routeLoader.reload()` and `allAppsLoader.refreshHomeMembership()` on the IO dispatcher. A successful import shows no UI feedback in this slice — the home grid re-renders, which is the feedback.
- The existing feed-provider rows, the unavailable footer, Back, and scrim dismissal all keep their current behavior. The existing `SettingsMenuOverlayTest` continues to pass without modification.
- The JSON codec rejects documents whose `kind` is not `proto-launch.home-layout/*`, whose `version` is not `1`, or whose required fields are missing/blank/non-positive. It produces a typed error per item for unknown `type`, missing app fields, missing shortcut fields.
- The import coordinator reflows oversized items left-then-up so no tile is silently lost. Items that cannot be reflowed are reported in `ImportResult`.
- The codec is pure: it has no Android dependencies beyond `org.json.JSONObject`/`JSONArray`. No `kotlinx.serialization`, no Gson, no Moshi.
- `:app` depends on no new Gradle modules. No new manifest permissions. No new transitive libraries.

## Required tests

Unit tests (`app/src/test/java/me/foxtails/lakehead/`):

- `HomeLayoutJsonTest` — round-trip with 0 / 1 / many placements, with and without pinned shortcuts, mixed orders. Reject tests: missing `version`, wrong `kind`, wrong `version` (`2`, `0`, `-1`), blank `home.id`, missing `columns`, item with missing `id`/`type`/`column`/`row`/`columns`/`rows`/`app`, item with negative column/row, item with non-positive columns/rows, app with missing `profileId`/`packageName`/`className`, shortcut with missing `packageName`/`profileId`/`shortcutId`, unknown `type` (per-item rejection, not whole-document rejection).
- `HomeLayoutExportImportTest` (instrumented or fakes) — reflow semantics: an item at `column=4, columns=2` on a 5-column grid is shifted to `column=3, columns=2` (left within row); an item that cannot fit in its row is shifted up; a colliding item is placed after the colliding earlier item.

Compose instrumented tests (`app/src/androidTest/java/me/foxtails/lakehead/`):

- Extend `SettingsMenuOverlayTest` (or add a new sibling test class) with:
  - `homeLongPress_showsLayoutDebugRows` — both rows present and clickable.
  - `exportRow_tapsLaunchesCallbackWithoutDismissingMenu` — menu remains visible after tap; no `selected` semantics on the row.
  - `importRow_tapsLaunchesCallbackWithoutDismissingMenu` — same.
  - `existingProviderRows_stillWorkAfterLayoutRowsAdded` — Lawnfeed pick still reports and dismisses.
  - `scrimAndBack_stillDismissMenu` — existing behavior unchanged.

## Explicitly deferred

- Cross-home import UI (mismatch warning, picker).
- Folder / widget item types.
- Bulk-replace repository operation. If a future slice needs atomic import, add `replaceHome(homeId, placements, bindings)` to `HomePlacementRepository` then.
- `:platform:documents` Gradle module.
- Auto-backup, periodic export, cloud sync.
- Any new manifest permission (SAF needs none).
- UI feedback for partial-import results (this slice reports them in `ImportResult` for future UI; the menu just re-renders the grid).

## Future implementation validation

Run these in order; one shell command per invocation per AGENTS.md §6:

```text
./gradlew :app:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :core:grid:test
./gradlew :core:designsystem:test
./gradlew :data:repository:test
./gradlew :data:database:testDebugUnitTest
./gradlew assembleDebug
./gradlew lint
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.SettingsMenuOverlayTest
./gradlew :feature:home:connectedDebugAndroidTest
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

State "NOT RUN" rather than implying a check passed. Per AGENTS.md §6, a successful unit build does not prove the SAF picker behavior; the device pass on `:app:connectedDebugAndroidTest` is required for any Android-only claim.

## Narrow exit gate

Complete when the transient settings menu surfaces two debug-only rows that drive a JSON export and a JSON import, the JSON codec is pure and round-trips the persisted home layout including per-tile pinned shortcuts, the import reflows oversized items instead of dropping them, all tests above pass, no manifest permission or new Gradle module is added, and the worktree only touches additive files plus the menu overlay.

## Completion evidence

- `HomeLayoutJsonTest` and `HomeLayoutExportImportTest` pass.
- `SettingsMenuOverlayTest` (existing + new tests) passes on device.
- All other `:app` and `:feature:home` unit + instrumentation tests pass with no regression.
- `assembleDebug`, `lint`, and the four `tools/scripts/*.py` audits pass with no new edges / new function or Compose findings.
- A handoff in `state-logs/handoffs/YYYY-MM-DD-home-layout-export-import.md` records baseline, touched paths, decisions, exact validation, unverified device behavior, and one concrete next step.
- `:app` module does not import any new dependencies.
- No Phase-10 dirty worktree files were modified (Phase 10 handoff is append-only).