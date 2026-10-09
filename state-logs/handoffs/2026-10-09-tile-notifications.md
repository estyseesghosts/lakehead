# Handoff: tile notifications (2026-10-09)

Task spec: `docs/agents/tasks/tile-notifications.md`. Owner: tile-notifications agent.

## Completed
- Slice 0: spec and `docs/adaptive_home_screen_tile_layout.md` §4 amended.
- Slice 1: count beside the icon on one-column and one-row tiles (`TileNotificationCount.kt`, `AppTile` `notificationCount`). Cap `9+` on columns, `99+` on rows; `fitPinnedStrip` gained `extraSlots`.
- Slice 2: newest-first preview region on large tiles (`TileNotificationPreview.kt`, `AppTile` `notificationPreviews`).
- Slice 3: `notificationsByTile` plumbed through `HomeCanvasTile`, `HomeCanvasGrid`, `HomeCanvas`, `LauncherShell`.
- Slice 4: `:core:model` `AppNotificationSummary.kt`; new module `:platform:notifications` (listener service, tracker, summary store, access helper). Depends only on `:core:model`.
- Slice 5: contact/large-icon photo extraction, bounded PNG disk cache, background attach and prune.
- Slice 6: `:app` `NotificationTileJoin.kt` (pure join on package + profile, widgets skipped; photo decode off-main), "Notification access…" settings row (`SETTINGS_MENU_NOTIFICATION_ACCESS_ROW_TAG`) opening the system listener screen. Menu height raised 280dp to 336dp.
- Lint fix: `getMessagesFromBundleArray` needs API 30 (min is 29); `latestMessage` returns null below R and callers fall back to title/text.

## Decisions
- Count tile = `columns == 1 || rows == 1`. Large tiles zero-pin and move the icon to the compact bottom-right anchor while previews exist; no scrolling in the tile; tapping a preview launches the app.
- The grid uses a bottom-row strip for every non-narrow span, so the planned "3x5 column strip" case does not exist; the preview region sits above the bottom icon row.

## Validation (exact)
- `./gradlew installDebug -q`: PASS (installed on R5CN70E2SGV).
- `./gradlew test -q`: PASS (only Robolectric native-access warnings).
- `./gradlew lint -q`: first run FAILED (NewApi above); after the fix PASS.
- `./gradlew assembleDebug -q`: PASS after the fix.
- `module_graph.py`: new edges `:app -> :platform:notifications`, `:platform:notifications -> :core:model` only.
- `function_audit.py`, `compose_audit.py`, `repo_map.py`: run; review signals only, nothing acted on. Compose audit flags are comments in existing files.
- `:core:designsystem` device class `AppTileNotificationCountTest`: PASS earlier on R5CN70E2SGV.

## Not verified
- Listener access has not been granted on the device, so no real notification has been seen on a tile. Post/remove reflected in the flow, preview rendering, photo extraction, and work-profile (PLG110) notifications are all unverified on device.
- Full `:core:designsystem:connectedDebugAndroidTest` failed earlier with "No compose hierarchies found" (launcher held focus) and was not re-run; existing `AppTileTest` is unconfirmed after the edits.
- No unit test for `joinNotificationsToTiles`; no Compose test for the preview.
- Selected-tile preview hiding at feature level is not tested.
- Release APK not built.

## Known gaps
- No fallback compact indicator on large tiles when the preview region is too small.

## Worktree
- Committed on `main` at the end of this session (see git log).

## Next action
Grant listener access (Settings row "Notification access…" on the device), post notifications from a messaging app and a work-profile app, and confirm count, preview, photo and removal on tiles. Exit condition: each of those observed on R5CN70E2SGV, then re-run the full `:core:designsystem:connectedDebugAndroidTest` and build the test-signed release APK per `docs/agents/tasks/release-builds-setup.md`.
