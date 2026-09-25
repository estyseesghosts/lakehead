# Launcher mockup implementation specification

These mockups describe a **logical free-placement tile grid**, a **symbol-based app list**, and **explicit internal hit regions**. The screenshots are visual references, not literal colors or grid lines to reproduce.

## 1. Logical home-screen grid

The white-grid mockup shows the **placement coordinate system only**.

The grid itself MUST NOT be visible in normal use.

- The screen is divided into uniform logical cells.
- On a normal phone, the mockup uses **six columns**.
- Rows continue vertically as required.
- Empty cells are transparent. They show wallpaper/background, not empty tile placeholders.
- There are no visible grid lines.
- The grid exists only for placement, collision detection, dragging, resizing, and layout.
- Items do not automatically collapse together.
- Empty space is valid.
- Apps can be placed at any valid unoccupied coordinate.

Use a data model like:

```kotlin
data class GridPlacement(
    val itemId: String,
    val column: Int,
    val row: Int,
    val columnSpan: Int,
    val rowSpan: Int,
)
```

Do **not** encode allowed sizes as an enum such as `SMALL`, `MEDIUM`, `LARGE`.

The current design includes:

```text
1×1
2×1
1×2
2×2
3×2
4×2
4×3
3×3
```

These are examples, not the complete set.

Validation should instead be generic:

```kotlin
fun GridPlacement.isValid(columnCount: Int): Boolean =
    column >= 0 &&
    row >= 0 &&
    columnSpan > 0 &&
    rowSpan > 0 &&
    column + columnSpan <= columnCount
```

Collision detection should also operate on rectangles rather than known tile types.

---

## 2. Grid geometry

All cells and tiles must share the same spacing system.

For six columns:

```kotlin
cellWidth =
    (availableWidth -
        horizontalInset * 2 -
        gap * (columnCount - 1)) /
    columnCount
```

A tile spanning multiple cells becomes:

```kotlin
tileWidth =
    cellWidth * columnSpan +
    gap * (columnSpan - 1)

tileHeight =
    cellHeight * rowSpan +
    gap * (rowSpan - 1)
```

This is important. A `4×2` tile is not an arbitrary rectangle that looks approximately four cells wide. It occupies exactly four grid columns and two grid rows.

### Spacing rules

Use shared layout tokens:

```kotlin
data class LauncherDimensions(
    val gridGap: Dp,
    val horizontalInset: Dp,
    val tilePadding: Dp,
    val symbolSize: Dp,
)
```

The mockup depends heavily on consistent:

- inter-tile gaps;
- internal padding;
- alignment;
- icon size;
- edge distance.

Do not add different padding for each tile size.

---

# 3. Free-form home layout

The home-screen mockup is intentionally **not** a conventional Android launcher grid.

Do not:

- sort items automatically;
- pack them toward the top-left;
- require complete rows;
- force tiles into a predetermined sequence;
- make every tile the same size;
- fill empty cells;
- render empty grid cells.

The following is valid:

```text
XXXX
X XX
X XX
 XX
 X
```

as long as the occupied cell rectangles do not overlap.

The saved layout should therefore contain explicit positions:

```kotlin
data class HomeItem(
    val id: String,
    val app: AppId,
    val placement: GridPlacement,
    val shortcuts: List<ConfiguredShortcut>,
)
```

The position is part of the user's layout, not something derived each time the launcher starts.

---

# 4. Home-screen app tiles

A tile is a themed rectangular surface occupying one or more grid cells.

The home screen should remain visually sparse. Do not automatically add standard Launcher3-style icon labels underneath every application.

## App symbol placement

The normal rule is:

> Place the app symbol at the bottom-right of its tile using the standard internal tile padding.

For a `1×2` tile:

> Place it at the bottom-center.

For example:

```kotlin
fun symbolAlignment(span: GridPlacement): Alignment {
    return if (
        span.columnSpan == 1 &&
        span.rowSpan == 2
    ) {
        Alignment.BottomCenter
    } else {
        Alignment.BottomEnd
    }
}
```

Do not implement different icon positioning separately for `2×2`, `3×2`, `4×3`, etc.

The layout rule should continue working when more spans are introduced.

---

# 5. App list

The app-list mockup is close to the existing conventional list, but its visual language should change.

Each row contains:

```text
┌────────────────────────────────────────────┐
│ SYMBOL   Application name    shortcut shortcut │
└────────────────────────────────────────────┘
```

The important characteristics are:

- uniform row geometry;
- app symbol on the left;
- app name beside it;
- configured shortcut actions on the right;
- no app-specific row color;
- no individual icon background shape;
- no card styling derived from the app;
- consistent spacing between rows.

The application's branding must not determine the surrounding launcher UI.

A suitable structure is:

```kotlin
Row {
    AppLaunchRegion(
        modifier = Modifier.weight(1f)
    ) {
        AppSymbol(...)
        AppName(...)
    }

    shortcuts.forEach { shortcut ->
        ShortcutRegion(shortcut)
    }
}
```

Critically, **do not make the entire Row one large clickable parent** and then place shortcut buttons inside it.

The launch area and shortcut areas should be sibling hit targets.

---

# 6. Symbol icons instead of Android icon backgrounds

Yes. Android adaptive icons make this partially possible.

An adaptive icon is normally composed from separate layers:

```text
AdaptiveIconDrawable
├── background
└── foreground
```

For this launcher, the desired result is approximately:

```text
AdaptiveIconDrawable
└── foreground only
```

This removes the adaptive icon's normal colored background and lets the foreground artwork act as the application's symbol.

On Android 13+, there is an even better possible source: the adaptive icon's **monochrome layer**, if the application supplies one.

The preferred source order should be:

```text
1. Adaptive monochrome layer, when available
2. Adaptive foreground layer
3. Legacy icon fallback
```

Conceptually:

```kotlin
fun loadSymbolIcon(
    packageManager: PackageManager,
    packageName: String,
): Drawable {
    val icon = packageManager.getApplicationIcon(packageName)

    if (Build.VERSION.SDK_INT >= 33 &&
        icon is AdaptiveIconDrawable
    ) {
        icon.monochrome?.let {
            return it
        }

        return icon.foreground
    }

    if (Build.VERSION.SDK_INT >= 26 &&
        icon is AdaptiveIconDrawable
    ) {
        return icon.foreground
    }

    return icon
}
```

In production, isolate the API-level-specific adaptive-icon access behind version-safe helpers.

### Important limitation

An adaptive icon's foreground is **not guaranteed to be a perfect standalone glyph**.

Some developers design the foreground assuming that:

- the background is present;
- the adaptive mask crops part of it;
- the foreground is oversized for parallax;
- artwork extends outside the normal safe area.

Therefore, extracting `foreground` is possible, but the result needs a normalization stage.

A useful abstraction is:

```kotlin
sealed interface AppSymbolSource {
    data class Monochrome(val drawable: Drawable) : AppSymbolSource
    data class AdaptiveForeground(val drawable: Drawable) : AppSymbolSource
    data class Legacy(val drawable: Drawable) : AppSymbolSource
}
```

Do not let icon extraction logic leak into the home-grid and app-list composables.

---

# 7. Symbol normalization

Every app symbol should occupy the same visual box regardless of the source drawable.

For example:

```kotlin
@Composable
fun AppSymbol(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier
            .size(LocalLauncherDimensions.current.symbolSize),
        contentScale = ContentScale.Fit,
    )
}
```

The icon renderer should handle:

- drawable bounds;
- transparent padding;
- foreground normalization;
- monochrome tinting;
- caching.

Do not solve these independently in `AppListRow` and `HomeTile`.

Use one symbol pipeline.

Something such as:

```text
Package/application
        ↓
Icon source resolver
        ↓
Monochrome / adaptive foreground / fallback
        ↓
Normalization
        ↓
Cached AppSymbol
        ↓
Home screen + app list + search + folders
```

This should become a launcher-level service rather than UI-specific code.

---

# 8. App shortcuts

The boat-wheel symbols are **placeholders for application shortcut actions**.

They are not permanent decorative icons.

Eventually these slots will represent actual configured Android app shortcuts.

For now:

```kotlin
sealed interface TileAction {
    data object LaunchApp : TileAction

    data class ShortcutPlaceholder(
        val slot: Int,
    ) : TileAction
}
```

or preferably:

```kotlin
data class ConfiguredShortcut(
    val id: String,
    val label: String?,
    val action: ShortcutAction,
)
```

with temporary placeholder actions supplied by test/mock data.

### Default state

An application with no configured shortcuts should have:

```kotlin
shortcuts = emptyList()
```

The launcher MUST NOT display two boat-wheel buttons beside every app by default.

The screenshots show **where configured shortcuts can go**, not mandatory controls.

---

# 9. App-list tap targets

The colored tap-target diagram describes code structure. The colors themselves are not UI colors.

For the app-list row:

```text
┌───────────────────────────────┬────────┬────────┐
│             BLUE              │ YELLOW │  RED   │
│ icon + application name       │ SC #1  │ SC #2  │
└───────────────────────────────┴────────┴────────┘
```

Meaning:

```text
BLUE   = launch application
YELLOW = shortcut action 1
RED    = shortcut action 2
```

Implement them as separate siblings:

```kotlin
Row {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onLaunchApp() }
    ) {
        // symbol + label
    }

    shortcuts.forEach { shortcut ->
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clickable {
                    onShortcut(shortcut)
                }
        ) {
            ShortcutSymbol(shortcut)
        }
    }
}
```

This is preferable to coordinate-testing pointer events inside one giant clickable composable.

---

# 10. Home-tile tap targets

The other diagram describes the internal behavior of a larger home tile.

Conceptually:

```text
┌────────────────────────────────────┐
│                                    │
│             APP BODY               │
│                                    │
│                                    │
├────────────┬────────────┬───────────┤
│ shortcut 1 │ shortcut 2 │ app icon  │
└────────────┴────────────┴───────────┘
```

In the mockup:

```text
Purple = launch application
Blue   = shortcut 1
Yellow = shortcut 2
Red    = launch application
```

The purple and red areas currently execute the same action.

**They should nevertheless remain separate hit regions in the implementation.**

This is deliberate.

The large body region is expected to gain different behavior later when the notification-center feature is added. If the body and app-icon area are collapsed into one monolithic clickable surface now, the tile will need structural rework later.

Represent the areas independently:

```kotlin
sealed interface TileHitTarget {
    data object Body : TileHitTarget
    data object AppSymbol : TileHitTarget
    data class Shortcut(val index: Int) : TileHitTarget
}
```

Current behavior:

```kotlin
when (target) {
    TileHitTarget.Body ->
        launchApp()

    TileHitTarget.AppSymbol ->
        launchApp()

    is TileHitTarget.Shortcut ->
        runShortcut(target.index)
}
```

Later this can become:

```kotlin
when (target) {
    TileHitTarget.Body ->
        openNotificationSurface()

    TileHitTarget.AppSymbol ->
        launchApp()

    is TileHitTarget.Shortcut ->
        runShortcut(target.index)
}
```

without changing tile geometry.

That separation should exist **now**, even though two callbacks currently do the same thing.

---

# 11. Do not make the hit map depend on colors

The diagnostic colors in the mockups should never appear in production code as layout concepts such as:

```kotlin
PurpleArea
BlueArea
RedArea
```

Use semantic names:

```kotlin
TileBody
AppLaunchButton
ShortcutSlot
AppSymbolButton
```

The diagrams describe interaction ownership, not styling.

---

# 12. Theme colors

The green/dark colors in the mockups represent **Material system colors**, not fixed launcher colors.

On Android 12+ use system dynamic colors/Monet.

For example:

```kotlin
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) {
            LauncherFallbackDarkColors
        } else {
            LauncherFallbackLightColors
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
```

Potential mappings:

```kotlin
val launcherBackground =
    MaterialTheme.colorScheme.background

val normalTile =
    MaterialTheme.colorScheme.surfaceContainer

val accentTile =
    MaterialTheme.colorScheme.primaryContainer

val normalSymbol =
    MaterialTheme.colorScheme.onSurface

val accentSymbol =
    MaterialTheme.colorScheme.onPrimaryContainer
```

The exact Material role can be adjusted during visual tuning.

The important rule is:

> Store semantic color roles, not RGB values.

Do not store:

```kotlin
Color(0xFF557A63)
```

because the screenshot happens to be green.

---

# 13. Recommended UI/data separation

Keep four concepts separate:

```text
Placement
    Where does this item exist?

Appearance
    How is the tile drawn?

Content
    Which application does it represent?

Interaction map
    Which parts perform which actions?
```

For example:

```kotlin
data class HomeTileModel(
    val id: String,
    val appId: AppId,
    val placement: GridPlacement,
    val appearance: TileAppearance,
    val shortcutSlots: List<ConfiguredShortcut>,
)
```

with:

```kotlin
data class TileAppearance(
    val colorRole: TileColorRole,
)
```

Do not store actual Material colors in persisted layout state.

```kotlin
enum class TileColorRole {
    Normal,
    Accent,
}
```

Then resolve it during composition.

---

# 14. Rendering architecture

This layout is sufficiently different from `LazyVerticalGrid` that it should not be forced into that abstraction.

The grid needs:

- arbitrary X/Y coordinates;
- arbitrary spans;
- gaps;
- collision detection;
- user-controlled placement;
- eventual drag/resizing;
- a vertically extending coordinate space.

Use a dedicated placement engine:

```text
HomeLayoutState
      ↓
GridPlacementEngine
      ↓
Visible grid bounds
      ↓
Tile geometry
      ↓
Compose renderer
```

The placement engine should be UI-independent.

For example:

```kotlin
data class CellRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right get() = left + width
    val bottom get() = top + height
}

fun CellRect.intersects(other: CellRect): Boolean =
    left < other.right &&
    right > other.left &&
    top < other.bottom &&
    bottom > other.top
```

This makes placement validation independent of density or screen pixels.

Only the rendering layer converts:

```text
cell coordinates → Dp/Px coordinates
```

---

# 15. Important Compose rule

Do not build the home screen as:

```kotlin
Column {
    Row { ... }
    Row { ... }
    Row { ... }
}
```

That makes arbitrary spanning and free placement increasingly difficult.

The renderer should work from absolute logical cell coordinates.

Conceptually:

```kotlin
@Composable
fun HomeGrid(
    items: List<HomeTileModel>,
    columns: Int,
) {
    CustomGridLayout(
        columns = columns,
        items = items,
    ) { item ->
        HomeTile(item)
    }
}
```

The layout system computes:

```kotlin
x = horizontalInset +
    item.column * (cellWidth + gap)

y = item.row *
    (cellHeight + gap)
```

and:

```kotlin
width =
    cellWidth * item.columnSpan +
    gap * (item.columnSpan - 1)

height =
    cellHeight * item.rowSpan +
    gap * (item.rowSpan - 1)
```

For a long/infinite home layout, this eventually also needs viewport-aware composition. Do not permanently compose hundreds of off-screen tiles merely because a custom `Layout` makes that easy.

---

# 16. Acceptance criteria

The implementation is correct when all of these are true:

1. Empty grid cells are invisible.
2. Grid lines are not rendered.
3. Six logical columns fit across the normal phone layout shown.
4. Tiles may leave arbitrary empty space.
5. Tiles do not automatically re-order or compact.
6. Tile spans are generic integer dimensions, not a hardcoded size enum.
7. Occupied tiles cannot overlap.
8. Gaps and internal padding remain consistent for every tile size.
9. Home-screen app symbols use bottom-right alignment except the `1×2` bottom-centered case.
10. App-list icons use the same symbol pipeline as home tiles.
11. Adaptive icon backgrounds are not intentionally rendered.
12. Monochrome/adaptive foreground artwork is preferred as the symbol source.
13. Applications do not receive arbitrary launcher background colors based on their own icon.
14. App-list shortcut regions are distinct from the app-launch region.
15. Home-tile body, app-symbol, and shortcut areas are distinct hit targets.
16. Body and app-symbol targets may both launch the app today but remain separate in code.
17. Shortcut controls are absent when no shortcuts are configured.
18. Boat-wheel symbols remain temporary placeholders, not the final shortcut design.
19. Material dynamic colors are used on Monet-capable Android versions.
20. Only the non-Monet fallback palette is statically defined.
