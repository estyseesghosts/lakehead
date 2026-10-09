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

## Slice 7 device evidence (R5CN70E2SGV, listener access granted)
- Count: a 1x1 Instagram tile showed `1` beside its icon for one real active notification.
- Preview: a 2x2 tile showed newest-first name + body with the icon at the compact bottom-right anchor (test notification via `cmd notification post`, temporarily mapped to a tile; mapping removed), and a 2x2 Instagram tile showed a real preview with a circular contact photo extracted from the notification.
- Ongoing notifications (a Discord service notification, LocalSend transfer) correctly produce no count or preview; archived notifications are ignored.
- Final clean build (no debug mapping) was installed with `installDebug`; the post-cleanup tile appearance was not re-screenshotted.
- `./gradlew :core:designsystem:connectedDebugAndroidTest` (full): PASS, including existing `AppTileTest` and `AppTileNotificationCountTest`.
- `./gradlew :app:testDebugUnitTest --tests ...NotificationTileJoinTest`: PASS (work/personal separation, widgets and quiet tiles skipped). Full `test`, `lint`, `assembleDebug` re-run after the join test and cleanup: PASS.
- `./gradlew :app:assembleRelease`: PASS, `app/build/outputs/apk/release/app-release.apk` (debug-keystore signed per `release-builds-setup.md`); not installed or run on the device.

## Not verified
- Work-profile (PLG110, user 150) notifications on device: shell cannot post to or list user 150 and none were active; only the pure join is unit tested for profile separation.
- Removal/dismissal propagating to a tile live, was only observed indirectly.
- Selected-tile preview hiding at feature level and a Compose test for preview rendering are not written.
- The release APK has not been run.

## Known gaps
- No fallback compact indicator on large tiles when the preview region is too small.

## Worktree
- Committed on `main` at the end of this session (see git log).

## Next action
Verify work-profile notifications on PLG110 (a work-profile app posting while a work tile is on Home) and install/run the release APK. Exit condition: the work tile shows its own count/preview separately from the personal copy, and the release build shows the same behaviour with the listener service intact under R8.
