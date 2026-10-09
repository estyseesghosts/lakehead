# Album tiles — photo slideshow on gallery app tiles

**Status:** In progress.

> **Roadmap note:** Media access is not part of the `AGENTS.md` §2 first-milestone order (0B → 1A → 1B → 1C → 1D). Like widgets and tile notifications, this is done ahead of the documented roadmap by explicit user decision (2026-10-09). It does not change that order. The ordering and ownership below are authoritative.

## Exit gate

Resizing a gallery app tile (package with a `gallery3d` segment) from below 2×2 to at least 2 columns and 2 rows offers an album slideshow. Accepting requests the photo permission if needed, lists device albums (name, cover, count), and binds the chosen album. The tile then shows the album's photos cropped to fill, crossfading every 15-minute clock slot, and advancing one photo per tap. The binding can be changed and removed from the edit menu. Full `AGENTS.md` §6 validation, a device pass and a handoff.

## Decisions (user-approved recommendations)

1. **Album source:** our own `MediaStore` album list. `READ_MEDIA_IMAGES` (API 33+), `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32) below. `READ_MEDIA_VISUAL_USER_SELECTED` is declared so Android 14+ partial access is detectable.
2. **On top of the photo:** no app icon when there are no pinned shortcuts; the shortcut strip over a bottom scrim when there are; no notification previews or counts on album tiles.
3. **Order:** newest first, sequential.
4. **Undo:** "Change album" and "Remove album" rows in the edit menu.
5. **Google Photos:** `com.google.android.apps.photos` does not match and never gets the prompt.
6. **Work-profile gallery tiles:** no prompt (the launcher reads personal-profile photos only).
7. **Layout export:** the binding is an optional export field; on import it is dropped when the album is not found on the device.

## Rules

- **Detection:** the package name has a dot-separated segment equal to `gallery3d`. Pure rule in `:core:model`.
- **Offer** (pure function) when all hold: gallery app tile, personal profile, not a widget, no album binding, old span not (≥2 columns and ≥2 rows), new span ≥2 columns and ≥2 rows. Large→large never re-prompts; "No" is not remembered, so the next crossing asks again.
- **Slideshow index:** `(clock slot + tap count) mod album size`, where slot = `epochMillis / 15 min`. Tap count is in memory only; process death resets it.
- The timer runs only while Home is visible and recomputes from the clock on resume. No alarms, no background work.
- Below 2×2 the tile renders its normal icon but keeps the binding; growing again restores the slideshow without a prompt.
- Empty album, deleted album, or revoked permission: the tile renders as a normal app tile; the binding is kept. Partial access shows the partial album honestly. Permission is re-requested only from the edit menu.
- The album key is `bucketId`; the display name is refreshed from `MediaStore` on listing.
- One or two downsampled bitmaps per tile are loaded off the main thread and invalidated by a `MediaStore` observer. No photo content, paths or URIs in logs or tracked files.
- Persist `bucketId` and name only; never URIs, bitmaps or `Cursor`s.

## Ownership

| Concern | Owner |
| --- | --- |
| `gallery3d` predicate, `AlbumRef(bucketId, name)`, `TileAlbumBinding` | `:core:model` |
| `tile_album_bindings` table (cascade on tile delete), migration 3→4, load/save/delete | `:data:database`, `:data:repository` |
| Permission state, album list, photo list per album, thumbnails, change observer | new `:platform:media` |
| Photo layer and crossfade in `AppTile` | `:core:designsystem` |
| Prompt overlay, album list overlay, `albumPhotoByTile` plumbing | `:feature:home` |
| Offer decision after resize, permission request, rotation clock and tap count, wiring | `:app` (`AlbumTileCoordinator`) |

Dependency direction is unchanged; `:platform:media → :core:model` only.

## Slices

| # | Slice | Exit |
| --- | --- | --- |
| 0 | This spec; media row in `docs/platform-capabilities.md`; roadmap exception | Docs agree |
| 1 | Package predicate and offer-decision function | Unit tests: matching and non-matching names, threshold crossing, already-bound, widget tiles |
| 2 | Model, table, migration, repository operations | Migration test 3→4; binding removed with its tile |
| 3 | `:platform:media` | Unit tests for mapping; device test lists albums and loads a thumbnail, reports denied and partial access |
| 4 | Photo layer in `AppTile` and plumbing through `:feature:home` | Compose test: photo at 2×2 and larger, icon below, edit border visible |
| 5 | Prompt and album list overlays | Compose tests for yes, no, cancel and empty list |
| 6 | Coordinator: resize → prompt → permission → list → save; rotation | Unit tests with a fake clock for the 15-minute step and tap advance; no timer while Home is hidden |
| 7 | "Change album" and "Remove album" rows in the edit menu | Binding can be changed and cleared on device |
| 8 | Exit check: `AGENTS.md` §6 validation, device pass on the Samsung, handoff | Handoff written |
