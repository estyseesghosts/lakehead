# Handoff: S4 — Shortcut Binding Persistence + Ordering Logic

**Date**: 2026-09-26
**Agent**: 04 (subagent)
**Scope**: S4 only — shortcut binding data persistence and pure ordering logic

## Completed work

### `:data:database` — schema v2 + shortcut binding entity + DAO + mapping
- **`DatabaseSchema.kt`**: VERSION 1 → 2
- **`ShortcutBindingEntity.java`**: New entity with `tileId` (PK, FK to `placements.itemId` CASCADE DELETE), `pinnedShortcutIds` (JSON TEXT)
- **`PlacementDatabase.java`**: Added `ShortcutBindingEntity` to `@Database` entities; added `MIGRATION_1_2` (CREATE TABLE + INDEX)
- **`PlacementDao.java`**: Added `upsertShortcutBinding`, `shortcutBinding`, `deleteShortcutBinding`, `shortcutBindingCount`
- **`PlacementMapping.kt`**: Added `TileBinding.toEntity()` / `ShortcutBindingEntity.toDomain()` using `JSONArray`/`JSONObject`
- **`PlacementDatabaseTest.kt`**: Updated schema version assertions from 1 → 2, added `shortcut_bindings` table/column/FK/PK assertions

### `:data:repository` — binding CRUD
- **`HomePlacementRepository.kt`**: Added `loadBinding(tileId)`, `saveBinding(binding)`, `deleteBinding(tileId)` to interface and `RoomHomePlacementRepository` implementation

### `:feature:home` — pure ordering logic
- **`ShortcutOrdering.kt`**: Pure `ShortcutOrdering` object with `visibleShortcuts`, `overflowShortcuts`, `pin` (null at-capacity), `unpin` (filter + collapse), `isLastUnpin`, `resize` (retains full list), `restore`, `layoutShortcuts` (CENTERED / APP_WITH_PINNED)

### Tests added
- **`ShortcutBindingPersistenceTest.kt`** (9 tests): schema v2, migration, roundtrip, cascade, upsert, empty, null, delete, independence, no-framework-fields
- **`ShortcutBindingRepositoryTest.kt`** (8 tests): load default empty, save/load roundtrip, upsert, delete, cascade, independence, empty binding
- **`ShortcutOrderingTest.kt`** (30+ tests): visible, overflow, pin (at-capacity null), unpin-collapse, isLastUnpin, resize-retain, restore, centered/app-with-pinned layout, purity (no mutation), combined workflow

### Other changes
- **`MainActivity.kt`**: Added `.addMigrations(PlacementDatabase.MIGRATION_1_2)` to database builder
- **`MainActivitySeededHomeDeviceTest.kt`**: Added migration to `withDatabase` builder
- **`AddToHomeCoordinatorTest.kt`**: Added `loadBinding`/`saveBinding`/`deleteBinding` to `FakePlacingRepository` and `ExplodingRepository`
- **`AllAppsRouteLoaderTest.kt`**: Added same methods to `FakeReadRepository`
- **`HomeRouteLoaderTest.kt`**: Added same methods to `FakeRepository`

## Validation performed
- `:data:database:test` — ALL PASS
- `:data:repository:test` — ALL PASS
- `:feature:home:test` — ALL PASS
- `./gradlew assembleDebug` — SUCCESS
- `./gradlew test` — ALL PASS (all modules)
- `./gradlew lint` — SUCCESS (0 errors, 0 new warnings)
- `python3 tools/scripts/repo_map.py` — new files visible
- `python3 tools/scripts/module_graph.py` — no new dependencies introduced
- `python3 tools/scripts/function_audit.py` — pre-existing warnings only
- `python3 tools/scripts/compose_audit.py` — pre-existing warnings only

## Touched paths
- `data/database/src/main/java/me/foxtails/lakehead/data/database/DatabaseSchema.kt`
- `data/database/src/main/java/me/foxtails/lakehead/data/database/ShortcutBindingEntity.java` (new)
- `data/database/src/main/java/me/foxtails/lakehead/data/database/PlacementDatabase.java`
- `data/database/src/main/java/me/foxtails/lakehead/data/database/PlacementDao.java`
- `data/database/src/main/java/me/foxtails/lakehead/data/database/PlacementMapping.kt`
- `data/database/src/test/java/me/foxtails/lakehead/data/database/PlacementDatabaseTest.kt`
- `data/database/src/test/java/me/foxtails/lakehead/data/database/ShortcutBindingPersistenceTest.kt` (new)
- `data/repository/src/main/java/me/foxtails/lakehead/data/repository/HomePlacementRepository.kt`
- `data/repository/src/test/java/me/foxtails/lakehead/data/repository/ShortcutBindingRepositoryTest.kt` (new)
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/ShortcutOrdering.kt` (new)
- `feature/home/src/test/java/me/foxtails/lakehead/feature/home/ShortcutOrderingTest.kt` (new)
- `app/src/main/java/me/foxtails/lakehead/MainActivity.kt`
- `app/src/androidTest/java/me/foxtails/lakehead/MainActivitySeededHomeDeviceTest.kt`
- `app/src/test/java/me/foxtails/lakehead/AddToHomeCoordinatorTest.kt`
- `app/src/test/java/me/foxtails/lakehead/AllAppsRouteLoaderTest.kt`
- `app/src/test/java/me/foxtails/lakehead/HomeRouteLoaderTest.kt`

## Decisions
- `ShortcutBindingEntity` is Java (matching existing entity style) with `@Insert(onConflict = REPLACE)` for upsert
- `loadBinding` returns empty `TileBinding` (not null/NotFound) when no binding exists — consistent with "default empty list" contract
- `ShortcutOrdering.pin` returns `null` at-capacity rather than throwing — caller decides how to surface the limit
- Layout enum is semantic-only; both CENTERED and APP_WITH_PINNED return the same list (positioning is a renderer concern)

## Known limitations
- Room schema v2 JSON not yet exported (generated on first test run; will be in `schemas/...PlacementDatabase/2.json` after build)
- No UI/anim/menu-rank/runtime/a11y work — this is data + pure logic only
- Device test for cascade delete not performed (no connected device)

## Worktree/commit state
- Uncommitted changes on current branch (no staging/commit performed per AGENTS.md §7)

## Next action
- Orchestrator should integrate `ShortcutOrdering` and binding persistence into the tile edit flow when the shortcut pinning UI slice is scheduled