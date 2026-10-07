# Icon cache slice 2: incremental refresh and ALPHA_8 decode (2026-10-07)

Baseline: HEAD `6aef799` (slice 1 committed). Objective: stop re-encoding and re-decoding the whole catalogue on every refresh; decode icons as alpha-only masks.

## Completed
- `AppIconSnapshotCache`: `replaceAll` removed. New `put(snapshot, AppIconProvenance)`, `retainOnly(identities)`, `provenanceOf(identity)`. Provenance = source stamp + encoder version. Unchanged entries keep their `AppIconSnapshot` instance.
- `LauncherAppsIconSnapshotRefresher`: drops identities that left the catalogue, skips entries whose stamp and encoder version match, otherwise looks up and encodes. A failed or missing lookup/encode keeps the last-good entry (and its old provenance, so the update is retried next refresh). An unknown stamp (`null`) is never fresh.
- `LauncherAppIconLookup.sourceStamp` (default null) and `LauncherAppIconPngEncoder.version` (default 1) added. `BoundedLauncherAppIconPngEncoder.ENCODER_VERSION = 1`.
- `LauncherAppsIconActivityLookup.sourceStamp` = `firstInstallTime * 31 + applicationInfo.sourceDir.hashCode()` from `LauncherActivityInfo`.
- `DecodedIconCache`: incremental. Reuses the decoded image when the snapshot instance is unchanged, decodes only new or changed ones, drops removed ones, keeps last-good per identity on decode failure, keeps everything on an empty input, returns the same map instance when nothing changed.
- `IconDecoding.kt` (`decodeAlpha8Icon`): decodes with `ALPHA_8`; if the codec returns another config it uses `copy(ALPHA_8)` and recycles the source; null on failure. `MainActivity` uses it.

## Decisions
- Staleness uses a stamp from `LauncherActivityInfo` instead of plumbing `LauncherApps` callbacks into a dirty set: callbacks already re-emit the catalogue, and the stamp works across all profiles without per-user `PackageManager` access. It relies on Android giving each install/update a new APK directory; this is a device assumption (see unverified).
- Stamp lookup costs one `getActivityList` call per app per refresh (no icon work).

## Touched paths
`platform/launcherapps/.../AppIconSnapshotCache.kt`, `LauncherAppsIconSnapshotRefresher.kt`, `app/.../DecodedIconCache.kt`, `IconDecoding.kt` (new), `MainActivity.kt`, tests `AppIconSnapshotCacheTest`, `LauncherAppsIconSnapshotRefresherTest`, `HomeRouteLoaderTest`, `app/src/androidTest/.../IconDecodingDeviceTest.kt` (new).

## Validation
- `./gradlew assembleDebug`: PASS. `./gradlew test`: PASS. `./gradlew lint`: PASS.
- `:app:connectedDebugAndroidTest` filtered to `IconDecodingDeviceTest` on SM-G986W (R5CN70E2SGV): 2 tests, 0 failures (ALPHA_8 config, alpha preserved, null on bad bytes).
- `module_graph.py`: no new edges. `function_audit.py`: no flags in changed files. `compose_audit.py`: not re-run (no composable changed).
- Unit coverage: 0 encodes on unchanged catalogue; added app only; updated package only; removal drops only its entry; encoder version bump re-encodes all once; failed lookup keeps last-good and retries; unknown stamp always re-encodes; decoder counting, same-instance reuse, no republish on no-op.

## Not verified
- On device: install/uninstall/update actually produce a changed stamp and a single-icon re-encode (no logging added); tinted rendering of ALPHA_8 icons in Home/All Apps; heap size with 500+ icons; slice 1's "no label-only rows past 64 apps".
- Colour-bearing icons look flattened (alpha-only) until slice 4 derives the mask from colour.

## Worktree
Slice 2 changes uncommitted and unstaged.

## Next action
Slice 3 (monochrome usability decision in `AppSymbolSourceResolver` only). Exit: resolver unit tests cover usable and unusable monochrome layers; no other module changes.
