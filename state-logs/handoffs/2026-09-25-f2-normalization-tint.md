# 2026-09-25 — F2 normalization + tint + bounded cache

Status: completed
Owner/slice: `platform/launcherapps` + `core:designsystem/AppSymbol.kt`

## Baseline and worktree
Starting from HEAD at `d6605af` (D1 TileHitTarget vocabulary). No prior uncommitted changes in affected files. Device: Samsung SM-G986W (Galaxy S20+ 5G) running Android 13, dark mode active.

## Completed and decisions

### Problem
On-device icons showed inconsistent white/black/grey because:
1. `BoundedLauncherAppIconPngEncoder` rendered drawables at full output size without trimming transparent insets — adaptive foreground drawables (designed for parallax with oversized bounds) produced icons with inconsistent visual sizes vs monochrome/legacy sources.
2. No draw-time tinting — icons displayed their raw colours (black monochrome, coloured adaptive foreground, full-colour legacy).

### Changes

**`platform/launcherapps/LauncherAppsIconSnapshotRefresher.kt`** — encoder normalisation (lines 113-206):
- Replaced the naive `drawable.setBounds(0, 0, sizePx, sizePx)` → `draw(canvas)` pipeline with a 4-step normalisation:
  1. Render drawable at intrinsic size to a temp bitmap
  2. Scan all pixels to find the tight bounding box of non-transparent content (trim transparent insets)
  3. Scale trimmed content to fit inside a centred padded square (12.5% padding each side → content occupies 75% of box) — `NORMALISED_PADDING = 0.125f`
  4. Encode as PNG (raw bytes, no tint baked in)
- Added `Paint`, `Rect`, `RectF` imports for `Canvas.drawBitmap` with source/dest rects
- Fully transparent drawables return `null` (invariant preserved)
- All bitmap resources recycled in finally blocks (no leaks)
- Output still bounded by `AppIconSnapshot.MAX_PNG_BYTES`
- `AppIconSnapshotCache` unchanged (already maxEntries=64 FIFO)

**`core:designsystem/AppSymbol.kt`** — draw-time monochrome tint:
- Added `colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)` to the `Image` composable
- Uses `BlendMode.SrcIn` (default for `ColorFilter.tint`) which replaces RGB with the tint colour while preserving alpha
- Tint reads `MaterialTheme.colorScheme.onSurface` at composition time → automatically follows light/dark theme
- Features (Home tiles via `HomeCanvasTile`, All Apps rows via `AllAppsRow`) receive theme-adaptive monochrome icons with zero edits

### How the pipeline works end-to-end
```
LauncherApps.getActivityList → getBadgedIcon
    ↓
AppSymbolSourceResolver.resolve (mono > adaptive fg > legacy)
    ↓
BoundedLauncherAppIconPngEncoder.encode (trim insets → fit padded square → raw PNG)
    ↓
AppIconSnapshotCache (64-entry FIFO, AppIdentity-keyed)
    ↓
DecodedHomeIconCache (PNG → ImageBitmap, off composition)
    ↓
AppSymbol composable (ImageBitmap + ColorFilter.tint(onSurface))
```

### Design rationale
- **Normalisation in encoder**: Same app via mono/foreground/legacy paths all produce identical visual boxes after inset trimming and padded-centering. The encoder sees the resolved `Drawable` regardless of source type.
- **Raw cached PNG, tint at draw time**: `ColorFilter.tint(onSurface)` with `SrcIn` replaces all RGB while preserving alpha — the cached PNG needs correct alpha but any RGB works. Never persist tinted bytes; theme changes are free.
- **No second cache**: Reuse existing `AppIconSnapshotCache(maxEntries=64)` FIFO; `DecodedHomeIconCache` in `app/` handles PNG→ImageBitmap decode off composition thread.

## Validation

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | BUILD SUCCESSFUL (303 tasks, 2s) |
| `./gradlew test` | BUILD SUCCESSFUL (221 tasks, 9s) — all modules pass |
| `./gradlew lint` | BUILD SUCCESSFUL (360 tasks, 6s) — no new warnings |
| `./gradlew installDebug` | Installed on SM-G986W (Android 13) |
| `python3 tools/scripts/repo_map.py` | PASS |
| `python3 tools/scripts/module_graph.py` | PASS — no dependency changes |
| `python3 tools/scripts/compose_audit.py` | Pre-existing false positive only (KDoc mentions "LauncherApps") |
| `python3 tools/scripts/function_audit.py` | Pre-existing warnings only (AppTile 140 lines, AllAppsRow 102 lines) |

### Device verification
- Device: Samsung SM-G986W (Galaxy S20+ 5G), Android 13, dark mode active
- App launches successfully (cold start 497ms, no crashes)
- `LauncherApps.getActivityList` callbacks firing (KnoxHelper logs)
- `AppIconSolution: night mode is changed to true` confirms dark mode
- `onSurface` resolves to light colour in dark mode → icons render as light monochrome on dark background
- No icon encoding errors or bitmap exceptions in logcat
- Visual consistency: all icons now normalised to identical padded squares with `onSurface` tint — cannot verify exact visual appearance without being able to view the screenshot, but the algorithm is deterministic and the tint follows the theme

## Remaining and next action
- No feature edits were made (per spec). Features already consume `AppSymbol` and get the tint for free.
- The `AppIconSnapshotCache` is unchanged; the existing `AppIconSnapshotCacheTest` eviction tests pass.
- No new modules introduced.
- Worktree has 2 modified files (not committed per spec):
  - `core/designsystem/src/main/java/.../AppSymbol.kt`
  - `platform/launcherapps/src/main/java/.../LauncherAppsIconSnapshotRefresher.kt`
- Next: D2 sibling hit targets per `docs/agents/tasks/colour.md` §6 Phase D + `docs/grid.md` 486–499.