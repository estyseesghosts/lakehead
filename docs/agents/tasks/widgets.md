# Widgets — real Android widget hosting on Home

**Status:** In progress. All slices below are **not started** except Slice 0 (this spec).

> **Roadmap note:** Widgets are Phase J in `docs/roadmap.md`, listed after 1D folders in AGENTS.md §2. This work is being done ahead of the documented roadmap order by explicit user decision. The slice ordering and ownership below are authoritative for implementation.

## Exit gate

A real third-party widget is discovered, added (with consent and configure where needed), hosted, moved, freely resized and removed. It survives a launcher restart and shows a placeholder after its provider is uninstalled. Full validation list, `:feature:home` and `:app` device suites, module graph check, and handoff written.

## Facts

- The grid is 5 columns (`columnsBound = 5`). A widget declared wider than 5 clamps to 5.
- `:core:grid` (`GridRules`) only sees an ID, position and span — legality, move, resize and remove need no change for widgets.
- Edit mode is entered only by long-pressing a tile today. An empty home returns early with "Home is empty" text and composes no grid.
- `HomeEditState` already supports editing with no selection (`Editing(selected = null)`).
- Resize is quantized by `RenderableSpans` to an allowlist that tops out at 5×4 and omits shapes such as 3×1, 4×1, 5×1.
- Persistence is app-only: `PlacementEntity` has a non-null foreign key to `app_references`; `toDomain()` rejects any type but `APP`.
- `firstFreeCell` in `:core:grid` searches for a 1×1 cell only.
- minSdk is 29, so the API 31 `targetCellWidth`/`targetCellHeight` fields need a fallback.

## Ownership

| Concern | Owner |
| --- | --- |
| `AppWidgetHost` / `AppWidgetManager`, provider discovery, ID allocate/bind/delete, host view creation, size options | `:platform:widgets` |
| Widget identity (provider component + profile, `appWidgetId`), sealed placement type | `:core:model` |
| Default-span maths, span-aware first-free search | `:core:grid` |
| Widget placement rows and migration | `:data:database`, `:data:repository` |
| (+) button, picker overlay, widget tile frame, resize rule, background long-press | `:feature:home` (button primitive in `:core:designsystem`) |
| Bind/configure activity results, wiring, host start/stop | `:app` |

Dependency direction is unchanged: `app → feature → core`, `app → platform → core`. `:feature:home` never sees a View or `:platform:widgets`. `HomeCanvas` gains a slot `widgetContent: @Composable (appWidgetId: Int, modifier: Modifier) -> Unit`, which `:app` fills with an `AndroidView` supplied by `:platform:widgets`.

State owners: provider catalogue and host in the platform gateway; persisted placement in the data layer; edit/gesture state in `:feature:home`; legality in `:core:grid`. Only the provider component, profile ID and integer `appWidgetId` are persisted; no `AppWidgetHostView`, `UserHandle` or pixels.

## Slices

Each slice is one verifiable change with its own validation run (AGENTS.md §6) and state-log checkpoint.

| # | Title | Module(s) | Status |
|---|-------|-----------|--------|
| 0 | Task spec and docs | docs | **done** |
| 1 | Free resize for all tiles | `:feature:home` | not started |
| 2 | Background long-press and empty home | `:feature:home` | not started |
| 3 | `:platform:widgets` discovery | `:platform:widgets` | not started |
| 4 | Default size and span-aware free search | `:core:grid` | not started |
| 5 | Domain and persistence | `:core:model`, `:data:database`, `:data:repository` | not started |
| 6 | Render model | `:feature:home` | not started |
| 7 | Hosting | `:platform:widgets`, `:feature:home` | not started |
| 8 | Touch routing | `:platform:widgets`, `:feature:home` | not started |
| 9 | (+) button and picker | `:core:designsystem`, `:feature:home` | not started |
| 10 | Add flow | `:app` | not started |
| 11 | Exit check | all | not started |

### Slice 0 — Task spec and docs

- Write `docs/agents/tasks/widgets.md` with this contract and exit gate.
- Note in the spec that widgets are being done ahead of the roadmap order by user decision.
- Update `docs/architecture.md` and `docs/platform-capabilities.md`: `:platform:widgets` is the owner of `AppWidgetHost` / `AppWidgetManager`.
- Start the state-log entry and point `state-logs/CURRENT.md` at it.

**Exit:** this file exists; architecture and platform-capabilities docs reference `:platform:widgets`.

### Slice 1 — Free resize for all tiles (`:feature:home`)

This comes first because it is independent of widgets and the widget work relies on it.

- Replace the `RenderableSpans.quantize` step in `HomeGridEditor` with a clamp: columns 1..grid width, rows ≥ 1. Legality stays with `GridRules`.
- Applies to app tiles and, later, widget tiles through the same path. No per-type resize rule.
- Check app-tile content at spans outside the old allowlist (3×1, 4×1, 5×1, 1×4, 5×5 and taller): `TilePinnedStrip.arrange`, symbol placement in `AppTile`, remove-button placement (`removeControlPlacement`) and the resize handle. Fix whatever does not degrade sensibly.
- Delete `RenderableSpans` if nothing else uses it; update `ResizeDragMath` docs and the tests that assert quantization.
- Tests: resize to previously unreachable spans, width clamp at the grid edge, collision still blocks, content arrangement at the new shapes.

**Exit:** on device, an app tile can be dragged to 5×1 and 3×1 and renders without overlap.

### Slice 2 — Background long-press and empty home (`:feature:home`)

- Long-press on empty grid background enters `Editing(selected = null)`. A tap on the background still exits edit mode.
- An empty home composes the grid surface instead of the text-only early return, so the long-press and the (+) button work with zero tiles. Keep the hold-reset guards that the early return currently protects.
- Confirm the long-press does not fight vertical scroll or the pager gate (`HomePagerGate`, `homePagerGesture`).
- Tests: state transition unit tests; device tests for long-press on background with tiles and on an empty home.

**Exit:** long-press on an empty Home enters edit mode with no tile selected; existing tile long-press still works.

### Slice 3 — `:platform:widgets` discovery

- New Gradle module, added to `settings.gradle.kts`.
- List providers across accessible profiles (`AppWidgetManager.getInstalledProvidersForProfile`) into a domain `WidgetProviderEntry`: provider identity (component + `ProfileId`), label, owning app label, declared size fields, resize limits, has-configure flag.
- Load off the main thread, cache, and invalidate on package/profile callbacks. No enumeration during recomposition.
- Tests: mapping unit tests; a device test that the list includes a known provider.

**Exit:** the module builds; provider list is populated and callback-invalidated; a device test confirms a known provider appears.

### Slice 4 — Default size and span-aware free search (`:core:grid`, pure)

- Default span from provider data:
  - API 31+ with `targetCellWidth`/`targetCellHeight` set: use them directly.
  - Otherwise: `cells = ceil((minDp + 30) / 70)` per axis.
  - Clamp columns to the grid width; minimum 1×1.
- Add `firstFreeRect(occupied, columns, span)` in row-major order, extending below current content when no hole fits. Keep `firstFreeCell` as the 1×1 case or fold it in.
- Tests: 4×2 stays 4×2; a 6-wide declaration clamps to 5; fallback formula at boundaries; holes, edges, collisions, empty grid.

**Exit:** pure tests pass; default-span maths and `firstFreeRect` cover all listed edge cases.

### Slice 5 — Domain and persistence

- `:core:model`: sealed `HomePlacement` with `AppPlacement` and new `WidgetPlacement(provider, appWidgetId)`; add `HomeItemType.WIDGET`.
- `:data:database`: migration v2→v3, additive only. New table `widget_bindings(itemId PK → placements ON DELETE CASCADE, appWidgetId)`, mirroring `shortcut_bindings`. The provider component and profile reuse the existing reference columns on `placements`. Bump `DatabaseSchema.VERSION` and register the migration in `MainActivity`.
- `:data:repository`: add `placeWidget`; `load` returns the sealed type; `remove` deletes the widget binding explicitly as it does shortcut bindings. Move and resize are already ID-based.
- Fix app-only callers to filter on type: All Apps home membership, the add-to-home duplicate check, `ShortcutMenuCoordinator`, `HomePlacementReconciliation`.
- Export skips widget placements (IDs are not portable); import is unchanged.
- Tests: migration from a v2 database, widget round trip, a mixed app/widget home through load, move, resize, remove and gap normalization.

**Exit:** a widget placement round-trips through the database; app-only callers no longer break on widget rows.

### Slice 6 — Render model (`:feature:home`)

- `HomeRenderTile` carries sealed content: `App(identity, label, icon, isLaunchable)` or `Widget(appWidgetId, provider, label)`.
- Mechanical change across `:feature:home`, `:app` callers and tests; no behaviour change. Kept separate because it touches many files.
- `HomeRenderProjection` maps both placement types.

**Exit:** all existing home tests pass; `HomeRenderTile` carries the sealed content type.

### Slice 7 — Hosting

- `:platform:widgets`: one app-scoped `AppWidgetHost` with a fixed host ID; `startListening` in `MainActivity.onStart`, `stopListening` in `onStop`. A function creates the host view for an `appWidgetId` from an activity context.
- `:feature:home`: `WidgetTile` is a tile frame around the slot, using the same lift, glide, selection and recess wrapper as `HomeCanvasTile`. `HomeGridLayout` dispatches on content type.
- Report the tile's dp size to the provider (`updateAppWidgetSize` on API 31+, options bundle below) on first layout and after each resize commit, not on every preview frame.
- A widget whose provider is missing renders an "unavailable widget" placeholder that can still be moved and removed.
- Tests: device test that a seeded widget row renders live content at the right cells.

**Exit:** a widget row seeded by a device test renders live content at the right cells.

### Slice 8 — Touch routing

- Normal mode: the widget owns its touches. A host-view subclass detects long-press (intercept plus timeout, cancelled on movement past slop) and calls the existing `enterOnLongPress`.
- Edit mode: a transparent Compose layer over the view takes taps for selection and blocks the widget, so the existing unified move/resize handler, resize handle and remove button work as for app tiles.
- Tile-origin pager hold applies to widget tiles as it does to app tiles.
- Device tests: a widget click reaches the widget; long-press enters edit with the widget selected; drag moves; handle drag resizes.

**Exit:** device tests confirm widget touch routing in both normal and edit modes.

### Slice 9 — (+) button and picker

- `:core:designsystem`: `AddButton`, a custom Foundation control alongside `RemoveButton` (not a Material FAB).
- `:feature:home`: the button is viewport-fixed at bottom-end above the nav-pill clearance (`bottomClearancePx`), outside the scrolling content, shown while editing and not dragging. It reports `onRequestAddWidget`.
- Picker: a floating overlay over Home (not a navigation destination) listing providers grouped by app, with label and default cell size. No preview images yet. Takes a plain list and reports the chosen provider.
- Opening the picker holds the pager like the settings overlay does.

**Exit:** (+) button appears during edit mode; picker lists providers; choosing one fires the callback.

### Slice 10 — Add flow (`:app` coordinator)

1. Allocate an `appWidgetId`.
2. `bindAppWidgetIdIfAllowed` under the provider's profile; if refused, launch the system `ACTION_APPWIDGET_BIND` consent prompt. No manifest permission is added.
3. If the provider has a configure activity, launch it through the host.
4. Compute the default span (slice 4), find the first free rect, `placeWidget`, reload.
5. Any cancel or failure deletes the allocated ID.

- The pending ID survives process death via saved instance state.
- A startup sweep deletes host IDs that have no persisted row.
- Removing a widget tile also calls `deleteAppWidgetId`.
- Tests: coordinator unit tests for each branch with fakes; device test for the happy path with a no-configure provider.

**Exit:** a real third-party widget is added through the full picker → consent → place flow; removing it cleans up the host ID.

### Slice 11 — Exit check

- A real third-party widget is discovered, added (with consent and configure where needed), hosted, moved, freely resized and removed.
- It survives a launcher restart and shows the placeholder after its provider is uninstalled.
- Full validation list, `:feature:home` and `:app` device suites, module graph check, handoff written.

**Exit:** the full widget exit gate passes.

## Risks

- Scrollable widgets (lists) compete with Home's vertical scroll and the pager. Expect a tuning pass in slice 8.
- Long-press then drag in a single gesture may not carry over from the View side at first; lift-then-drag will work. Record it as a follow-up if so.
- Free resize ignores provider min/max hints, so some widgets render badly when very small. This is accepted.
- Very large or oddly shaped app tiles may expose layout assumptions in `AppTile`; slice 1 is where those surface.

## Out of scope

- Widget preview images in the picker.
- Backup/restore `appWidgetId` remapping and widget export/import.
- Replacing an expanded app tile with its widget (roadmap J6) and the Show All Widgets projection (J7).
- Visual design of the widget tile frame, button and picker.

## Suggested order

0 → 1 → 2 are small and user-visible on their own. Then 3 → 4 → 5 → 6 → 7 gets a seeded widget rendering on device. Then 8 → 9 → 10 completes the user-facing flow, and 11 closes the gate.