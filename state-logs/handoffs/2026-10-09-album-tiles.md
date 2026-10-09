# 2026-10-09 — Album tiles (photo slideshow on gallery app tiles)
Status: in progress
Owner/slice: album-tiles agent; `docs/agents/tasks/album-tiles.md`

## Baseline and worktree
HEAD `37e1a85` on `main`, clean tree at session start. Database at version 3. No media module or photo permission existed. The user asked for slices 0–8 to be done in order, committed as each lands, and `installDebug` on the connected phone.

## Completed and decisions
- Slice 0: task spec written, media row added to `docs/platform-capabilities.md`, roadmap exception recorded in the spec.
- Slice 1: `isGalleryPackage` / `isAlbumTileSpan` / `shouldOfferAlbum` in `:core:model` (`AlbumTileOffer.kt`), unit tested (`AlbumTileOfferTest.kt`, 11/0).
- Slice 2: `AlbumRef(bucketId, name)` + `TileAlbumBinding` in `:core:model`; `tile_album_bindings` table (`AlbumBindingEntity`, cascade on tile delete), `MIGRATION_3_4`, version 4 with exported `4.json`; DAO ops; `load/save/deleteAlbumBinding` on `HomePlacementRepository` (explicit delete in `remove()` alongside the cascade); 7 `:app` test fakes extended; `MIGRATION_3_4` registered in `MainActivity`, the seeded-home device test and the widget migration test. Version-pinning tests updated 3→4 (`PlacementDatabaseTest` now also covers the new table/keys/`4.json`).
- Slice 3: new `:platform:media` (`MediaAccess`/`MediaAccessQuery`, `MediaAlbumAggregation`, `MediaStoreGateway` with album/photo/thumbnail queries + change observer; manifest photo permissions) depending only on `:core:model`; `MediaAlbum`/`AlbumPhoto` in `:core:model`. Partial access (API 34+) is unit-covered; its device proof moves to the user's PLG110 (Android 16) pass in slice 8.
- Slice 4: photo layer in `AppTile` (`AlbumPhotoLayer` full-bleed cropped `Crossfade`, `isAlbumPhotoSpan`, strip scrim; previews suppressed while shown; `:core:designsystem` gains the animation dep) and `albumPhotoByTile` plumbing `HomeCanvasTile` ← `HomeCanvasGrid` ← `HomeCanvas` ← `LauncherShell` (defaults keep everything photo-free until slice 6). Fixed a real NPE the first device run caught: photo-only tiles fell into the strip branches with a null `stripFit`.
- All seven decisions from the plan were adopted as the plan recommended them (see the spec).

## Validation
- Slice 0 is docs only; no build run.
- Slice 1: `./gradlew :core:model:testDebugUnitTest --tests "me.foxtails.lakehead.core.model.AlbumTileOfferTest"`: 11 tests, 0 failures.
- Slice 2: `:core:model` 46/0, `:data:database` 28/0, `:data:repository` 42/0, `:app` unit 154/0 (all 0 failures/errors); `assembleDebug` PASS; `lint` PASS.
- Slice 3: `:platform:media` unit 14/0 (access decision incl. partial, aggregation, Robolectric denied/granted); device 5/5 on R5CN70E2SGV (API 33): granted FULL, album list with count/cover, newest-first photos, thumbnail, observer on insert+delete, self-revoked denied yields nothing; full `test` 1541/0, `assembleDebug` PASS; `lint` PASS.
- Slice 4: `:core:designsystem` device `AppTileAlbumPhotoTest` 9/9 (photo at 2×2/4×2, icon on 1×1/2×1/1×2, edit-selected photo+selection, strip+scrim over photo, preview suppression, photo change); full designsystem device suite 117/0 (one `NavigationControlGeometryTest` launch flake on the first full run, 14/14 alone and 117/0 on re-run); full `test` 1541/0, `assembleDebug` PASS, no new module edges; `lint` PASS.

## Remaining and next action
Slices 5–8. Next: slice 5, the prompt and album-list overlays in `:feature:home`.
