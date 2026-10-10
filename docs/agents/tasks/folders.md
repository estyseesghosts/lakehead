# Folders — persistent folders that open as floating overlays

**Status:** Planned. No slice has started; Slice 0 is this spec plus the doc fixes it lists.

> **Roadmap note:** Folders are Phase E / 1D in `docs/roadmap.md`. This spec replaces the E1–E4 outline there where the two differ. User decisions recorded here (2026-10-09): five columns, not six; folder members are any grid tile at any span; a 1×1 folder tile previews its first four members as a 2×2 grid; auto-dissolve is in; drag and drop between Home and a folder is out for now.

## Exit gate

On a phone, a user can create a folder from a Home tile, add app tiles and widget tiles of any span to it, open it as a floating overlay over Home, launch from it, move tiles back to Home, and remove it. A folder left with one member dissolves into that member; a folder left with none disappears. Everything survives a launcher restart, and no unrelated Home tile moves. Full validation list (AGENTS.md §6), `:feature:home` and `:app` device suites, module graph check, and handoff written.

## Facts

Read from source on 2026-10-09; re-verify before relying on them.

- No source file mentions folders. `HomeItemType` has `APP` and `WIDGET`; `HomePlacement` is sealed over `AppPlacement` and `WidgetPlacement`.
- The grid is already five-wide: `GridColumns.NORMAL_PHONE_REFERENCE_COUNT = 5`. Only docs still say six (see Slice 0).
- `PlacementEntity` has `NOT NULL` `profileId` / `packageName` / `className` with a foreign key to `app_references`. A folder has no app, so the current schema cannot hold one.
- `PlacementEntity.toDomain` throws on any `itemType` other than `APP` or `WIDGET`.
- `widget_bindings`, `shortcut_bindings` and `tile_album_bindings` are all keyed by a placement `itemId` with cascade delete.
- `DatabaseSchema.VERSION` is 4. Migrations 1→2, 2→3 and 3→4 only added tables; none rebuilt one. `exportSchema = true`.
- `RoomHomePlacementRepository` computes Home occupancy from `dao.placementsForHome(homeId)` in `load`, `placeItem`, `edit`, `remove` and `persistShifted`.
- `GridFirstFreeRect` in `:core:grid` already does span-aware first-free search.
- `HomeRenderTile.appIdentity` is non-null; widgets are an `appWidgetId` nullable on the same type.
- `HomeCanvas` already takes a widget content slot filled by `:app`; `:feature:home` never sees a View.
- `LauncherShell` in `:app` hosts the widget picker, settings and album overlays and turns off pager swipe while one is open.
- `HomeLayoutCodec.encodeLayout` takes `List<AppPlacement>`; widgets are not exported today.

## Design decisions

1. **A folder member is an ordinary placement with a parent.** `placements` gains a nullable `parentFolderId` referencing `placements(itemId)` with cascade delete. A member keeps its `itemId`, type, span, availability and every binding row, so a widget keeps its `appWidgetId` and an app tile keeps its pinned shortcuts and album when it moves in or out. There is no separate members table.
2. **A folder is a placement row with no app.** `itemType = FOLDER`, and the three app-reference columns become nullable. Both changes land in one v4→v5 table rebuild. This is the first non-additive migration and needs a migration test against the exported v4 schema.
3. **Two grids, one rule set.** Home occupancy is the rows of a Home with `parentFolderId IS NULL`. A folder's interior is the rows with `parentFolderId = <folder>`. Both use `GridRules`, `GridRowGapNormalizer` and the same adapted column count (five on a normal phone). No compaction, no auto-sort, holes are valid, in either grid.
4. **Members may be any type and span except a folder.** App tiles and widget tiles at any legal span. A `FOLDER` row with a non-null parent is rejected: no nesting.
5. **Default member size is 1×1; existing tiles keep their span.** A tile moved in from Home keeps the span it had. Anything created inside a folder starts at 1×1.
6. **The closed folder tile is 1×1 and not resizable for now.** Only the 1×1 preview is specified, so resize handles are hidden on folder tiles until a larger preview is designed.
7. **Preview:** a 2×2 grid of the first four members in row-major order (row, then column) of the folder's interior grid. Every member occupies one preview cell whatever its span. Fewer than four members leave cells empty. A widget member shows its provider's app symbol.
8. **Create, add and move-out are edit-menu actions.** No drag between Home and a folder in this spec. Placement on the receiving grid is the first free rectangle for the tile's span.
9. **Auto-dissolve.** When an operation that takes a member out of a folder leaves it with exactly one member, the folder is replaced by that member in the same transaction. When it leaves none, the folder row is deleted. Details below.
10. **Removing a non-empty folder** asks for confirmation, then deletes the folder and all members with their bindings. Widget members release their `appWidgetId` through the existing host path. Nothing spills onto Home.
11. **Duplicates follow Home's rule.** The same app may appear in a folder as often as Home allows it on Home; each occurrence is a distinct item with its own bindings. **Recorded in Slice 0:** the database only enforces a unique `itemId`; the sole duplicate guard is `AddToHomeCoordinator`, which returns `AlreadyOnHome` when a Home placement has the same `AppIdentity`. Every folder entry point moves an existing item and never mints a new app item, so folders add no new duplicate path and need no new guard. After Slice 3 that coordinator sees top-level rows only, so an app inside a folder does not block adding it to Home again; accepted.
12. **No folder name in this spec.** Not specified in any doc; add by its own migration when designed.
13. **The overlay lives in `:feature:home`**, not a new `:feature:folders` module. With members of any span and widgets, the folder interior is a second instance of the Home grid, and a separate feature module could only reach the grid editor by duplicating it. `docs/roadmap.md` E2 allows a bounded Home feature. Update `docs/architecture.md` to match.

### Auto-dissolve rules

- **Trigger:** only an operation that removes a member (move to Home, remove member). Creating a folder with one member does not dissolve it; a folder that never gains a second member stays until its member leaves or it is removed.
- **One member left:** the survivor takes the folder's Home position with its own span, replacing the folder row (`GridRules.place` with `replacingId`). If that span does not fit there, it goes to the first free rectangle on Home. Home is vertically unbounded and the span is no wider than the column count, so this always succeeds.
- **None left:** delete the folder row; normalize row gaps as any remove does.
- **Not a trigger:** a member becoming `UNAVAILABLE` after its package is uninstalled. It still counts as a member.
- **Open overlay:** if the dissolved folder is open, the overlay closes.
- The whole step is one transaction with the removal that caused it. A failure rolls back both.

## Ownership

| Concern | Owner |
| --- | --- |
| `FolderPlacement`, `HomeItemType.FOLDER`, parent reference on placements | `:core:model` |
| Legality, first-free search, row-gap normalization for both grids | `:core:grid` (no change expected) |
| v5 schema, migration, DAO queries scoped by parent | `:data:database` |
| Folder create, member load, transfers, dissolve, remove | `:data:repository` |
| Folder tile primitive with 2×2 preview | `:core:designsystem` |
| Closed tile on Home, folder overlay, edit-menu rows, confirm prompt | `:feature:home` |
| Loading, metadata join, launch, overlay hosting, Back and pager guard, widget release | `:app` |

Dependency direction is unchanged: `app → feature → core`, `app → data → core`. No new Gradle module and no new module-graph edge.

State owners: persisted folder structure in the data layer; which folder is open and its edit state in `:feature:home`; legality in `:core:grid`. Persist item ids, parent ids and logical cells only.

## Slices

Each slice is one verifiable change with its own validation run (AGENTS.md §6) and state-log checkpoint.

| # | Title | Module(s) | Status |
|---|-------|-----------|--------|
| 0 | Spec and doc fixes | docs | done |
| 1 | Domain model | `:core:model` | not started |
| 2 | Schema v5 | `:data:database` | not started |
| 3 | Parent-scoped Home repository | `:data:repository` | not started |
| 4 | Folder repository: create, load, transfer | `:data:repository` | not started |
| 5 | Auto-dissolve and folder remove | `:data:repository` | not started |
| 6 | Folder tile primitive | `:core:designsystem` | not started |
| 7 | Closed folder tile on Home | `:feature:home` | not started |
| 8 | Grid canvas reuse audit | `:feature:home` (read-only) | not started |
| 9 | Folder overlay | `:feature:home` | not started |
| 10 | Wiring, launch and dismissal | `:app` | not started |
| 11 | Edit-menu actions | `:feature:home`, `:app` | not started |
| 12 | Catalogue reconciliation and export | `:app` | not started |
| 13 | Exit check | all | not started |

### Slice 0 — Spec and doc fixes

- Replace six with five in `AGENTS.md:44`; `docs/roadmap.md` lines 396, 434, 539, 582, 624, 1142, 1158, 1423; `docs/launcher_report.md` §19 and its Phase 6 and summary lines.
- Point `docs/roadmap.md` Phase E at this file and note where this spec overrides it (member spans, widgets, menu-based transfer, dissolve).
- Change `docs/architecture.md` so folders are owned by `:feature:home`, not a planned `feature:folders`.
- Check whether `SixCellGeometry.kt` in `:core:designsystem` encodes a six-cell assumption or is a stale name. Log a follow-up; do not rename in this slice.
- Confirm whether Home allows the same app on two tiles and record it under decision 11.
- Start the state-log entry and point `state-logs/CURRENT.md` at it.

**Done 2026-10-09.** Follow-up logged: `SixCellGeometry.COLUMN_COUNT = 6` in `:core:designsystem` is a live six-column assumption (not just a stale name); check its callers when Slice 6/9 need cell geometry and use the adapted column count instead.

**Exit:** no live doc says six for Home or folders; decision 11 is filled in.

### Slice 1 — Domain model (`:core:model`)

- Add `HomeItemType.FOLDER` and `FolderPlacement` (home id, item id, position, span, availability; no app reference).
- Add `parentFolderId: ItemId?` to `HomePlacement`; `FolderPlacement` requires it to be null.
- Fix the exhaustive `when` sites this breaks (`referenceIdentity()` and any others the compiler finds).

**Tests:** construction invariants; `withGeometry` keeps identity and parent; a nested folder is rejected.

**Exit:** module compiles and tests pass; no behavior change for existing types.

### Slice 2 — Schema v5 (`:data:database`)

- `MIGRATION_4_5`: rebuild `placements` with nullable app-reference columns and a nullable `parentFolderId` (foreign key to `placements(itemId)`, cascade delete, indexed). Copy every row with `parentFolderId = NULL`.
- Mapping: `FOLDER` with a non-null app reference, or `APP`/`WIDGET` with a null one, is a corrupt row and is rejected, not reinterpreted.
- DAO: top-level placements for a Home; members of a folder; member count.

**Tests:** migration from the exported v4 schema preserves every app, widget, shortcut-binding and album-binding row; deleting a folder cascades to members and their bindings; mapping rejects corrupt combinations.

**Exit:** bump `DatabaseSchema.VERSION` to 5 with the migration registered wherever 1–4 are.

### Slice 3 — Parent-scoped Home repository (`:data:repository`)

- Every Home occupancy read in `RoomHomePlacementRepository` uses top-level rows only.
- `move` and `resize` evaluate legality against the target's siblings (same parent), so they work on members without a new API.
- `remove` on a member removes that member only.

**Tests:** a member never collides with or is shifted by a Home tile and the reverse; move/resize of a member is judged against its folder's grid; existing repository suites pass unchanged.

**Exit:** no path counts a member as Home occupancy.

### Slice 4 — Folder repository: create, load, transfer (`:data:repository`)

New `FolderRepository` interface over the same database, sharing one internal sibling-grid helper with the Home repository.

- `createFolder(homeId, fromItemId)`: places a 1×1 folder at the tile's origin and moves the tile in at (0, 0) with its span.
- `loadFolder(homeId, folderId, columns)`: members with normalized positions.
- `moveIntoFolder(itemId, folderId, columns)` and `moveToHome(itemId, columns)`: update parent and position to the first free rectangle on the receiving grid. Bindings are untouched.
- Typed results: not found, wrong home, not a folder, would nest, rejected.

**Tests:** spans above 1×1 and widgets transfer both ways with bindings intact; a tile wider than the folder grid cannot occur at equal column counts (assert it); rollback on rejection; unrelated rows unchanged.

**Exit:** all four operations are single transactions with tests.

### Slice 5 — Auto-dissolve and folder remove (`:data:repository`)

- Apply the auto-dissolve rules inside `moveToHome` and member `remove`.
- `removeFolder(homeId, folderId)`: deletes folder, members and bindings; returns the `appWidgetId`s it dropped so `:app` can release them.
- Results report whether a dissolve happened and which item replaced the folder.

**Tests:** 2→1 replaces the folder at its position; survivor too large for that position lands at first free; 1→0 deletes the folder; creating a one-member folder does not dissolve; an unavailable member still counts; failure rolls back the removal too.

**Exit:** every dissolve rule above has a test.

### Slice 6 — Folder tile primitive (`:core:designsystem`)

- `FolderTile`: a 1×1 tile surface holding a 2×2 grid of up to four symbols, using the shared gap/padding tokens and the existing symbol rendering.
- State in, events out; no model or repository types beyond what `AppTile` already takes.

**Tests:** geometry of the four preview cells; zero to four symbols; one hit target for the whole tile.

### Slice 7 — Closed folder tile on Home (`:feature:home`)

- Start with one finder question: every consumer of `HomeRenderTile.appIdentity` and `isWidget`.
- Give the render tile a folder variant carrying up to four preview entries; the projection picks them in row-major order and ignores span.
- Tap emits an open-folder event. Edit mode allows select, move and remove; resize handles are hidden.

**Tests:** projection order and the four-entry bound; a 4×2 member occupies one preview cell; folder tile is not launchable and not resizable.

### Slice 8 — Grid canvas reuse audit (read-only)

One question: can `HomeCanvasGrid`, `HomeGridEditor` and the gesture files render and edit a second placement list inside a bounded, independently scrolling frame without Home-specific assumptions (pager gate, rest offset, edge auto-scroll, nav clearance)?

**Exit:** a written answer in the handoff listing each Home-only assumption and the smallest parameter change for each. This decides the shape of Slice 9:

- **Reusable:** Slice 9 hosts the existing grid and editor, and members can be moved and resized inside the folder.
- **Not cheaply reusable:** Slice 9 renders a read-and-launch grid only; in-folder move and resize become a follow-up spec. Members still keep their spans and sit where first-free put them.

### Slice 9 — Folder overlay (`:feature:home`)

- `FolderOverlay`: floating panel over Home on `FloatingOverlayFrame`, five cells across on a normal phone (column count is an input), independent vertical scroll.
- Renders app tiles at their spans and widget tiles through the same widget content slot Home uses.
- Events: launch, shortcut, dismiss, and (if Slice 8 allows) the Home edit events scoped to the folder.

**Tests:** frame geometry; column count comes from input; scrolling the overlay does not scroll Home; a 2×2 and a widget member lay out at their spans.

### Slice 10 — Wiring, launch and dismissal (`:app`)

- `FolderCoordinator`: loads members off the main thread, joins catalogue metadata and icons, launches under the full `AppIdentity`.
- `LauncherShell` hosts the overlay, adds it to the pager-swipe guard, dismisses on Back and outside tap, and closes it when its folder dissolves or is removed.
- Hosted widget views inside the overlay follow the same host start/stop as Home.

**Device tests:** open, launch a work-profile member under the right profile, a widget member renders and updates, dismiss without navigating, state after restart.

### Slice 11 — Edit-menu actions (`:feature:home`, `:app`)

- On a Home tile: "New folder" and, when a folder exists, "Add to folder" with a folder chooser.
- On a member: "Move to Home" and "Remove".
- On a folder tile: "Remove folder" with the confirm prompt when non-empty; `:app` releases dropped `appWidgetId`s.
- Dissolve results update Home and close the overlay.

**Tests:** menu entries per selection type; coordinator unit tests for each action and for dissolve; device pass of create → add → move out → dissolve.

### Slice 12 — Catalogue reconciliation and export (`:app`)

- Members follow package add/remove the way Home placements do: marked unavailable, never deleted.
- Layout export/import carries folders and their app members. Widget members are skipped exactly as Home widgets are today. Decide and record whether the format version changes.

**Tests:** reconciliation on a member; export/import round trip with a folder; an older document without folders still imports.

### Slice 13 — Exit check

- Full validation list; `:feature:home` and `:app` device suites on R5CN70E2SGV.
- Device pass: create, add app and widget, tile above 1×1 in a folder, open, launch, move out, dissolve at one and at zero, remove non-empty folder, restart, uninstall a member's package.
- Module graph shows no new edge. Handoff written; `CURRENT.md` updated.

## Risks

- **Table rebuild.** The v4→v5 migration is the first that rewrites `placements`. A mistake loses the user's Home. Mitigate with the exported-schema migration test and a device upgrade from a populated v4 install before merging Slice 2.
- **Missed occupancy read.** Any query that still returns members as Home rows corrupts Home legality. Slice 3 must enumerate every caller of the old DAO query.
- **Grid editor reuse.** `HomeCanvasGrid` is large and tied to Home's viewport. Slice 8 exists so this is known before the overlay is built.
- **Widgets in an overlay.** Hosted views inside a scrolling floating panel may fight the panel for touch. Reuse the Home touch-routing rules and verify on device in Slice 10.
- **Dissolve surprises.** A survivor larger than 1×1 can land away from where the folder was. The rule is deterministic; confirm on device that it reads as acceptable.

## Out of scope

- Drag and drop between Home and a folder, and drop-on-tile folder creation.
- Folder tiles larger than 1×1 and their previews.
- Folder names, nested folders, folders in All Apps, the Work Apps group.
- Wide-viewport and split-pane folder behavior (`docs/roadmap.md` M3).
- Exporting widgets, on Home or in folders.

## Suggested order

0 → 1 → 2 → 3 → 4 → 5, then 6 → 7, then 8 → 9 → 10 → 11 → 12 → 13. Slices 6 and 8 touch no persisted code and can run alongside 2–5 if a second agent is available. After Slice 10 folders persist, open and launch; after Slice 11 the exit gate is reachable.
