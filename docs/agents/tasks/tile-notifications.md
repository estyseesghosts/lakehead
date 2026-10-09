# Tile notifications — count and preview on Home tiles

**Status:** In progress.

> **Roadmap note:** Notification surfaces are Phase H work, ahead of the `AGENTS.md` §2 order. Like widgets, this is done ahead of the documented roadmap by explicit user decision (2026-10-09). The ordering and ownership below are authoritative.

## Exit gate

Real notifications posted on the device appear on the matching tiles: a count next to the icon on small tiles (one column or one row) and a newest-first preview (photo, name, body) on large tiles. Access is granted through the system listener settings; revoking it clears everything. Work-profile notifications attach to the work-profile tile. Full `AGENTS.md` §6 validation and a handoff.

## Decisions (user-approved recommendations)

1. Column tiles match the digit to the **visible glyph**, not the 40dp box; floor about 24dp, tuned on device.
2. Count cap: `9+` on one-column tiles, `99+` on rows.
3. Zero-pin large tiles: the icon drops to the compact bottom-right anchor while notifications exist and re-expands when they clear.
4. No scrolling inside the tile in this version (it would fight Home scroll and drag). This overrides `docs/adaptive_home_screen_tile_layout.md` §4 "scrollable".
5. All non-ongoing, non-summary notifications get previews; the photo only when present.
6. Tapping a preview launches the app. Opening the notification's own intent is a follow-up.

## Rules

- "Small" for the count = `columns == 1 || rows == 1` (new predicate; distinct from `isLargeTileSpan`).
- The count sits immediately left of the app icon (icon keeps its anchor). On 1×1 the pair is centred. Icon and number share one colour (the tile's content colour) and one extent. Number uses `FontFamily.SansSerif`, tabular figures.
- Row tiles with pins: the count takes one strip slot and the displaced shortcut joins `+N`.
- 1×N with pins: only the bottom app slot becomes the shrunk pair.
- Large tiles: preview region = tile − padding − strip. Rows are newest first: ~20dp circular photo, name on one line, body; last row body ellipsised to fill. Hidden while edit-selected.
- Accessibility: count and newest sender merge into the tile body `stateDescription`.
- Count = active notifications per package and profile, excluding group summaries and ongoing ones.
- Data from the notification itself (MessagingStyle sender `Person` icon/name, then large icon, then conversation title / `EXTRA_TITLE`). No `READ_CONTACTS`.
- In memory only. No history database. No notification content in logs.

## Ownership

| Concern | Owner |
| --- | --- |
| `NotificationSourceKey(packageName, ProfileId)`, `AppNotificationSummary(count, previews)` | `:core:model` |
| `NotificationListenerService`, normalisation, access state, photo extraction, summaries `StateFlow` | new `:platform:notifications` |
| Count fit maths, preview region maths, rendering | `:core:designsystem` |
| `notificationsByTile` through `HomeCanvas` → `HomeCanvasGrid` → tile | `:feature:home` |
| Joining summaries to tiles, photo decode cache, settings entry | `:app` |

Dependency direction: `:platform:notifications → :core:model` only.

## Slices

| # | Slice | Exit |
| --- | --- | --- |
| 0 | This spec, adaptive-layout doc amendment | Docs agree |
| 1 | Count indicator in `AppTile`, caller-supplied; pure fit function | Unit tests: 1×1, 1×3, 2×1, 5×1, with/without pins, 1–2 digits |
| 2 | Preview region in `AppTile`; pure region geometry, rows-that-fit | Unit tests: 2×2, 4×2, 2×3, 3×5 column strip; no overlap with strip/edit controls |
| 3 | Plumbing through `:feature:home`; hidden on selected tile | Existing unit tests pass |
| 4 | Model types, `:platform:notifications`: listener, pure normaliser, access state | Normaliser unit tests; device post/remove reflected |
| 5 | Contact photos off main thread, bounded cache, decode in `:app` | Messaging notification yields photo; missing icon yields none |
| 6 | `:app` join and settings row opening listener settings | Real notifications on tiles |
| 7 | Exit check: full §6 validation, work profile, handoff | Handoff written |
