# 2026-10-07 — Icon cache slice 1: one encoded cache, one decoded cache, no 64 cap
Status: completed (device check NOT RUN)
Owner/slice: `:platform:launcherapps` (encoded cache) and `:app` wiring (decoded cache). Part of the four-slice unified icon cache plan (slice 2 incremental + ALPHA_8, slice 3 monochrome usability, slice 4 colour-mask derivation are NOT started).

## Baseline and worktree
HEAD `196b500`, worktree clean at start. Changes are uncommitted and unstaged.

## Completed and decisions
- `AppIconSnapshotCache`: `maxEntries` removed along with the FIFO eviction; memory is bounded per entry by the 96px size and `MAX_PNG_BYTES`.
- `DecodedHomeIconCache` renamed `DecodedIconCache` (file `app/.../DecodedIconCache.kt`), `maxEntries` and the `while (size > maxEntries)` eviction removed.
- `MainActivity`: one `iconSnapshots`, one `iconRefresher`, one `decodedIcons`; both route loaders receive the same instances.
- `HomeRouteLoader` now refreshes the whole catalogue (was the placed subset). Required because the shared cache is replaced from a complete set; a subset refresh would drop every unplaced icon. `AllAppsRouteLoader` unchanged apart from comments.
- `LauncherAppsIconSnapshotRefresher.refresh` is serialised by an internal `Mutex` since the refresher is now shared by two loaders.
- `AllAppsRow` shows an outlined placeholder (`ALL_APPS_ICON_PLACEHOLDER_TAG`) when the icon is null, so a failed encode is visible and the label stays aligned.
- Tests: removed cap/eviction tests; added 500-entry tests for the encoded cache and the refresher; added `homeAndAllAppsLoadersSharingOneCacheResolveTheSameIcons` (100 entries, shared caches, identical icon maps). Updated `HomeRouteLoaderTest` expectations from placed-only to whole-catalogue icons.
- Cost to know about: Home now encodes and decodes the full catalogue on each refresh. Slice 2 (incremental) removes that cost; slice 1 alone is therefore heavier on startup.

## Validation
- `./gradlew :platform:launcherapps:testDebugUnitTest :app:testDebugUnitTest :feature:allapps:testDebugUnitTest` PASS
- `./gradlew assembleDebug` PASS
- `./gradlew test` PASS (no failures reported)
- `./gradlew lint` PASS (no errors reported)
- `repo_map.py`, `module_graph.py` (no new edges), `function_audit.py` (`AllAppsRow` 122 lines, flagged as a review signal only), `compose_audit.py` (only comment/test string matches): run, no action.
- grep: no `maxEntries = 64` for icons; one `AppIconSnapshotCache()` in `MainActivity`.
- Not added: a Compose test for the placeholder (no UI test run for it); device instrumentation NOT RUN.

## Remaining and next action
- Unverified on device: no All Apps row is label-only with more than 64 launchable activities.
- Next: slice 2 (diff-based refresher, dirty marking from `LauncherApps` callbacks, `lastUpdateTime` stamp, `ENCODER_VERSION`, incremental decoded cache, ALPHA_8 decode). Exit condition per the plan: counting-fake tests show 0 encodes on an unchanged catalogue and 1 on a single added app.
