# Design contract

> Active repair contract: `lakehead-grid-repair-plan.md` §3 replaces the historical MOVE clamp/re-freeze/budget rules that were previously stated in this file. Those rules are retained below only as the superseded record they are.

## Frozen Home grid contract (repair plan §3)

These rules are the acceptance contract for every remaining repair slice. They are **not** yet all implemented; each slice records its own status in `state-logs/CURRENT.md`.

1. **Logical origin is permanent:** column 0, row 0. Persisted rows and columns are never negative.
2. **Horizontal space is bounded by `columns`; vertical logical space is nonnegative and effectively unbounded.** The visible viewport or edit workspace is not a legality bound.
3. **Leading rows are real.** A tile at row 5 has five logical rows above it. Content height is `max(row + rowSpan)`, never `maxRow - minRow`.
4. **One immutable `HomeGridMetrics` owns every logical↔pixel transform.** Rendering, hit testing, move, resize and overlays all consume it.
5. **Scroll is applied exactly once:** viewport Y → content Y is `viewportY + scrollY`.
6. **A visual rest offset may center a short grid, but it is presentation-only.** It is frozen for the whole edit session and never animates underneath a drag.
7. **Move is absolute, not accumulated:** current pointer + current scroll − immutable grab offset → requested top-left cell.
8. **Move preserves span. Resize preserves position.** These are type/state invariants, not late validation.
9. **Invalid requests stay invalid.** A raw out-of-bounds requested cell is never clamped into a different legal cell; the preview may hold its last valid value.
10. **Collision is logical rectangle intersection only.** Pixel geometry never decides placement legality.
11. **Edit workspace size controls only actual scrollable content height.** It can never make a logical cell legal or illegal.
12. **One gesture chooses one route.** After slop it is MOVE or RESIZE until release or cancel.
13. **Persistence starts only after release.** Gesture code emits a commit intent and knows nothing about database acknowledgement.
14. **Repository-normalized accepted placements are authoritative.** When gap normalization moves further tiles, the state owner reconciles from that accepted result.
15. **Home renders all current tiles directly.** No Home row-window virtualization until real measurements demand it.

### Explicitly retired

Negative persisted Home rows, drag-time origin animation, Home row-window rendering, clamped candidate cells, edit-legality frontiers and persistence acknowledgement leases are **retired contracts**. Do not add, extend or re-document them. Superseded mechanics — `frozenScrollBaselinePx` algebra, structural re-freeze, live move-frame mirrors, row-origin mapping, finite legality-row coupling, signed/rebased renderer support, the eight-step MOVE generation budget — are on the deletion list for repair plan Phase 9, not a pattern to imitate.

Normal-mode centering survives as an optional visual offset. It is presentation only: never a logical row origin, and never a coordinate frame that moves under a stationary pointer.

## Superseded Home MOVE rules (historical — do not implement)

- ~~MOVE span fitting clamps a candidate to `[0, columns-span.columns] × [0, legalityRows-span.rows]`; a span that cannot fit is observable as `OUT_OF_BOUNDS`, with no free-cell search.~~ Retired by contract rule 9: clamping is removed and an out-of-bounds request stays invalid rather than becoming another legal cell.
- ~~Each MOVE drag generation permits eight successful scroll-state changes in either direction, including exits and re-entry; no-ops are free and the cap stops the ticker, not the drag.~~ Retired: the budget was documented but never enforced, and continuous edge hold is the target behavior (plan Slice 6.3).
- ~~Resize arm is sampled at slop and preserved through transfer routing.~~ Route choice itself survives as contract rule 12; the specific arm-sampling and transfer-routing mechanics above are superseded by plan Slices 4.1–4.3.

The sketches described in `first_draft.md` specify interaction, not final colors or typography. ProtoLaunch uses many custom launcher-owned UI primitives and should intentionally not look like a stock Android app. Launcher-owned surfaces use custom Compose Foundation, layout, text and drawing primitives rather than stock Material product components. `docs/grid.md` plus `docs/grid/` photos are the visual/interaction authority for the Home grid, tile symbols, app list, and hit regions; this file records the stable rules distilled from them.

- Home and opened floating folders use **five square 1×1 cells across** as the normal-phone baseline arrangement. It is a reference layout, not a hard-coded screen contract: available grid width is an input, and different screen shapes and grid widths must be supported while preserving stable logical content. Available width minus padding and the applicable column gaps determines cell width. A tile spanning two cells includes the intervening gap. The placement grid itself is never visible: no grid lines, no empty-cell placeholders; empty cells show wallpaper and are valid user layout. Tiles never auto-sort, auto-compact, pack toward top-left, require complete rows, or fill empty space. The sole persistence exception is `GridRowGapNormalizer`, applied as the final step within the same database transaction for an accepted mutation or load: gaps over two completely empty rows may shift whole occupied sections upward, never during drag preview and never removing leading/trailing space.
- Logical spans are generic positive integer dimensions, not a size enum. The launcher starts from 1×1, 2×1, 1×2, 2×2 and 4×2; `docs/grid.md` adds 3×2, 4×3 and 3×3 as valid examples of the open set. Placement rules belong to `core:grid` (bounds + rectangle collision; edge-touching is legal); which sizes the UI can render/edit belongs to later tile/editing slices.
- Home tiles are themed rectangular surfaces sharing one gap/padding/symbol-size token system. Small tiles place app symbols bottom-right except 1×2 bottom-center; a large zero-pin tile centers its symbol using the minimum of available width and height after padding, without reserving shortcut-strip space. Branding never drives surrounding UI: no app-derived row colors, icon background shapes, or card styling. App-list rows are uniform geometry — symbol left, name beside it, configured shortcuts right — with sibling launch vs shortcut hit targets, never nested clickables. Shortcut slots render only when configured; boat-wheel artwork is a temporary placeholder, not a default control.
- One shared symbol pipeline serves Home, app list, search, and folders: adaptive monochrome preferred, then adaptive foreground, then legacy fallback, followed by bounds/padding normalization, tinting, and bounded caching. Icon extraction and decoding stay off the UI thread and out of composables.
- Tile hit targets are semantic and separate now: `TileBody` / `AppLaunchButton` (`AppSymbolButton`) / `ShortcutSlot`. Home-tile body and app symbol both launch today but remain distinct regions so the body can later open the notification surface without geometry rework. Never encode diagnostic mockup colors (`PurpleArea`, `BlueArea`) as layout concepts.
- Theme uses semantic roles, not screenshot RGB: Material dynamic colors on Android 12+ (`background`, `surfaceContainer`/`primaryContainer`, `onSurface`/`onPrimaryContainer` or tuned equivalents); only the non-Monet fallback palette is statically defined. Persist `TileColorRole.Normal/Accent`, never resolved colors.
- Reusable primitive geometry is separate from screen-specific arrangement. `:core:designsystem` provides custom Compose primitives and small geometry/layout contracts; `:feature:*` chooses how each screen arranges navigation, controls, overlays and other UI. Arrangement may use explicit anchors, regions, insets and measured geometry—for example bottom, top, side or floating placement. Screens must not assume a bottom navigation bar or fixed coordinates. These layout contracts do not create a second placement or packing system: `:core:grid` remains the sole owner of placement legality, extent and projection.
- Four destinations: News Feed, Message Center, Home Grid and All Apps. Home has its own frequent-app row. News remains a shell until a source is defined. Navigation appearance and placement are screen arrangement decisions, not part of the reusable primitive geometry contract.
- Long press enters edit mode; movement, resize, remove, grouping and supported widget replacement validate against grid placement. Folder opens above the current page, scrolls independently and dismisses without navigating.
- Expanded app tiles are launcher-owned surfaces, not Android widgets. Real hosted widgets use a distinct rendering path.
- Persist multiple home pages even though only the first is enabled initially. Wide displays can reflow deterministically without rewriting phone placement.
- All Apps (the App Library) uses grouped tiles with permanent Recent Files, Recent Messages and Recent/Frequent Apps groups, plus a permanent Work Apps group/folder; a gesture reveals an alternate list with shortcuts. Work-profile apps are not mixed into personal app groups or marked with badges: assign entries to Work Apps by profile identity and retain each full profile-aware `AppIdentity` (including `ProfileId`, e.g. profile 11) for distinct entries and correct-profile launch. Show an explicit unavailable or empty state when the work profile cannot be accessed or has no launchable apps. Search aggregates independent providers. Message Center uses normalized notification history, deterministic filters and priority rules. This is a later App Library presentation contract; Phase A catalogue behavior remains unchanged.

Slice 0A only displays a placeholder; the behavior above is the contract for subsequent slices.
