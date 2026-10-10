Some of those points were only partly present. The previous version **did not state clearly enough** that:

* both homes are single, vertically scrolling, effectively unbounded canvases;
* tiles are freely placeable rather than packed by default;
* the grid visually scrolls underneath floating controls;
* filtered/Tetris views must stop above those controls for hit-testing;
* navigation and filter chips are specifically custom-drawn floating components;
* the navigation geometry requires compact concentric radii.

I also rechecked the Android/platform claims. `LauncherApps.startShortcut()` is the correct launcher API for app shortcuts, `AppWidgetHost` is specifically intended for home-screen-style widget hosts, and `NotificationListenerService` supplies posted/removed/ranking notification events. ([Android Developers][1]) Lawnfeed remains Lawnchair's Google-feed companion, while Neo Feed explicitly supports launchers with custom feed-provider support, including Lawnchair. Current Lawnchair also exposes selectable alternative feed providers. ([GitHub][2])

# Launcher implementation specification — revised

## 1. Core design model

This launcher must **not** inherit the normal Launcher3 interaction model.

It consists of four principal surfaces:

1. **News Feed**
2. **Message Center**
3. **Home Grid**
4. **All Apps**

Home itself consists of **two independent vertical canvases**:

```text
Home 1
└── independent placements
    independent folders
    independent widgets
    independent expanded tiles

Home 2
└── independent placements
    independent folders
    independent widgets
    independent expanded tiles
```

Home 1 is enabled by default.

Home 2 exists as an optional second layout and is disabled by default.

Neither home uses horizontal pages.

---

# 2. Visual layer hierarchy

The launcher Home must be treated as several independent visual layers.

```text
TOP
│
├── floating overlays
│   ├── tab/filter chips
│   ├── search
│   ├── recent/frequent app circles
│   ├── navigation pill
│   └── opened folders/modals
│
├── vertically scrolling tile canvas
│
├── wallpaper
│
BOTTOM
```

The wallpaper is the visual base.

The Home grid is transparent and rendered over it.

Floating controls are rendered above the grid.

### Important behavior

The tile canvas is allowed to **scroll visually underneath the floating controls**.

Do not reserve permanent blank layout space around:

* navigation;
* search;
* filter chips;
* frequent-app buttons.

This allows the launcher chrome to genuinely float over the wallpaper and content.

Hit-testing must still ensure that the visible controls receive input before obscured tiles.

---

# 3. Navigation bar

The four-destination navigation control is a completely custom component.

Do not use:

* `NavigationBar`
* `NavigationBarItem`
* standard Material bottom navigation
* a full-width bottom container

Its appearance is:

```text
        floating above wallpaper

      ╭───────────────────╮
      │ ○    ○    ○    ○ │
      ╰───────────────────╯
```

Buttons:

1. News Feed
2. Message Center
3. Home Grid
4. All Apps

## Geometry

The navigation must be:

* horizontally centered;
* compact;
* detached from screen edges;
* pill-shaped;
* composed of four circular buttons;
* positioned above the bottom safe/system inset.

Use **concentric radii** throughout the component.

The geometry must derive from common dimensions rather than several unrelated corner-radius constants.

For example:

```kotlin
navHeight
buttonDiameter
outerPadding

outerRadius = navHeight / 2
buttonRadius = buttonDiameter / 2
```

The circular buttons and pill therefore appear geometrically related.

Spacing and sizing must be controlled by our layout code.

---

# 4. Custom component requirement

The major launcher controls are intentionally custom-built.

Compose primitives may be used internally, but do not build the design by composing stock Material components and overriding a few parameters.

Custom components include at least:

```text
LauncherNavigationPill
LauncherNavigationButton

LauncherTabStrip
LauncherTabChip

LauncherSearchField

LauncherFrequentAppButton

LauncherTile
LauncherFolderTile
LauncherFolderOverlay

LauncherFilterControls
LauncherResizeControls
```

This is required because the launcher needs precise control over:

* geometry;
* radii;
* clipping;
* placement;
* pressed states;
* transitions;
* morphing;
* spring animation;
* drag interaction;
* content overlap.

---

# 5. Tab/filter chips

Filter tabs are also custom components.

The visible tab itself is a compact **pill-shaped bubble**, using the same general visual language as Beeline's custom tab chips.

Conceptually:

```text
invisible layout host
│
├── ( Home 1 )
├── ( Home 2 )
├── ( Widgets )
└── ( Apps )
```

The parent strip/container itself must not appear as another bar behind them.

It exists only to provide:

* positioning;
* gesture handling;
* spacing;
* animation coordinates;
* measurement.

The visible surfaces are the individual chips.

No Material `TabRow` or standard segmented control.

Selection should support animated:

* size;
* position;
* background;
* content emphasis.

---

# 6. Base phone grid

On a normal non-folding phone, Home uses a grid exactly:

**6 × 1x1 cells wide.**

Horizontal width is therefore finite:

```text
0 1 2 3 4 5
```

Vertical height is not fixed.

A grid item is stored with:

```kotlin
data class GridPlacement(
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
)
```

All launcher-owned Home objects use this coordinate system.

That includes:

* apps;
* expanded apps;
* folders;
* hosted widgets.

---

# 7. Home is one vertically scrolling canvas

Each Home is a **single vertically scrolling layout**.

There is:

* no page 1/page 2/page 3;
* no horizontal paging;
* no fixed number of rows;
* no artificial maximum Home height.

Conceptually:

```text
row 0
row 1
row 2
...
row 40
...
row 500
...
```

The implementation may necessarily use finite integer coordinates, but the user-facing model is an indefinitely extendable vertical Home.

The rendered content height is derived from:

```text
highest occupied row
+ bottom working margin
+ current viewport requirements
```

As tiles are moved farther downward, the Home extends with them.

---

# 8. Free placement

The normal Home layout is **not automatically compacted**.

Tiles are freely placeable anywhere that their required rectangle is empty.

For example:

```text
[X][ ][ ][X][ ][ ]
[ ][ ][ ][ ][ ][ ]
[ ][ ][XXX ][ ][ ]
```

Empty gaps are intentional and valid.

Moving an item must not cause unrelated items to move automatically.

A placement succeeds only when:

```text
inside horizontal grid bounds
AND
all occupied cells are free
```

Dragging toward the current bottom extends the canvas.

This behavior applies independently to Home 1 and Home 2.

---

# 9. Independent Home layouts

Home 1 and Home 2 must have entirely separate placement maps.

For example:

```text
App A
Home 1 → row 3, column 0, 1x1
Home 2 → row 14, column 4, 2x2
```

These are two placements, not one object whose coordinates change when switching views.

An application may therefore:

* exist on both;
* exist on only one;
* use different sizes on each;
* use different shortcut configurations on each.

Widgets and folders follow the same rule.

---

# 10. Filter projections

Filters may combine content from both Home layouts.

This distinction is important:

```text
normal Home
    = manually positioned canvas

filtered view
    = generated projection
```

Required combined filters include:

* **Show All Apps**
* **Show All Widgets**

When Show All Apps is selected:

```text
apps from Home 1
       +
apps from Home 2
       ↓
combined projection
```

When Show All Widgets is selected:

```text
widgets from Home 1
          +
widgets from Home 2
          ↓
combined projection
```

Do not mutate the source layouts.

Duplicate handling must be defined by logical placement identity. The same app intentionally pinned on both homes represents two Home placements, but the combined Apps projection should normally expose the application once unless later product requirements explicitly require placement-level duplicates.

---

# 11. Filtered Tetris layout

Combined filters use the specified bottom-weighted **Tetris** placement algorithm.

Normal Home does **not**.

The generated layout should:

1. obtain item dimensions;
2. identify the usable visible width;
3. establish a bottom anchor;
4. fill upward;
5. fill available holes with compatible tile dimensions;
6. never overlap items;
7. produce deterministic output.

Example:

```text
       [1x1][1x1]
[2x1][2x1][1x1][1x1]
[2x2][2x2][2x2]
────────────────────
       bottom
```

---

# 12. Special filtered-view bottom boundary

This modifies the normal overlay rule.

In ordinary Home:

> grid content may scroll underneath the controls.

In a generated Tetris/filter view:

> the bottom-packed items must be placed **above the interactive launcher controls**.

They must not be packed underneath:

* navigation pill;
* frequent-app buttons;
* search;
* any filter controls occupying the same bottom area.

Define a calculated:

```kotlin
interactiveBottomBoundary
```

from the current visible control geometry.

The Tetris packer uses that boundary as its visual floor.

This ensures every generated tile remains visible and clickable.

Do **not** solve this with a fixed hard-coded bottom padding. The boundary must derive from actual measured overlay geometry.

---

# 13. Tile types

Every application initially occupies:

```text
1 × 1
```

Supported expanded forms:

```text
1 × 1
2 × 1
1 × 2
2 × 2
4 × 2
```

The model must support new dimensions later without changing its basic persistence design.

An expanded application remains the same logical Home tile.

Do not model these as unrelated widget classes.

---

# 14. 1x1 app tile

A `1x1` tile is the base application representation.

Tap:

```text
launch application
```

Long press enters contextual/edit behavior.

Required actions include:

* move;
* resize;
* remove from Home;
* app information;
* expose supported shortcuts.

---

# 15. 2x1 and 1x2 expanded apps

These sizes expose application **quick shortcuts**.

Android launchers can query launcher shortcuts and invoke them through `LauncherApps`; `startShortcut()` specifically requires shortcut-host access. ([Android Developers][1])

Example:

```text
┌─────────────┐
│ Messages    │
│ Alice  Bob  │
└─────────────┘
```

or:

```text
┌───────┐
│Camera │
│Selfie │
│Video  │
└───────┘
```

The user selects which available shortcuts occupy the tile.

Do not invent undocumented private intents when the application exposes a proper launcher shortcut.

---

# 16. 2x2 and 4x2 expanded apps

Larger application tiles can expose:

* more quick actions;
* unread state;
* recent notification summaries;
* conversation information;
* richer application-specific launcher information.

Example:

```text
┌─────────────────────────┐
│ 1 unread message        │
│ Alice: Are you coming?  │
│                         │
│ [Alice] [Bob] [Search]  │
└─────────────────────────┘
```

A `4x2` variant can allocate more area for notification summaries or media controls.

Layouts are size-specific.

Do not scale the smaller UI uniformly.

---

# 17. Converting an expanded app to a widget

A sufficiently large application tile can be replaced with one of that application's real Android widgets.

Required interaction:

```text
App tile
↓
expand to supported large size
↓
Use Widget
↓
select compatible provider
↓
replace launcher-generated tile with AppWidget
```

Android provides `AppWidgetHost` specifically for applications such as home screens that embed widgets, and `AppWidgetHostView` supplies the actual hosted view. ([Android Developers][3])

Keep the platform widget as a real hosted Android view.

Do not reimplement arbitrary third-party widgets in Compose.

---

# 18. Folders

A folder is another grid object.

Closed state:

* occupies grid cells;
* shows a miniature contents preview.

Open state:

* does not navigate away from the current surface;
* becomes a floating overlay;
* remains above the underlying grid.

Conceptually:

```text
overlay controls
│
├── opened folder
│
├── normal launcher chrome
│
└── scrolling Home beneath
```

---

# 19. Open folder grid

On an average phone, the opened folder's app grid is also:

**6 × 1x1 cells wide.**

It may vertically scroll when necessary.

The opened folder must support:

* tap to launch;
* drag reorder;
* drag into/out of folder;
* contextual app actions;
* dismiss on Back;
* dismiss on outside tap.

Its visual panel floats over the open Home/All Apps view.

---

# 20. Frequent app circles

The four small circles shown above the primary navigation are launcher-generated frequent/recent application targets.

Exactly four should be visible in the intended phone layout.

They are visually independent circular controls.

A local launcher usage model can track:

* last launch;
* launch count;
* weighted recent use.

This does not require Android Usage Access simply to track launches initiated through our launcher.

---

# 21. All Apps category view

All Apps does **not** initially open as a traditional alphabetical app drawer.

It first displays applications grouped into category tiles.

The same group/folder visual language used on Home should be reused here.

Categories are generated automatically, with support for manual user correction.

Do not tightly couple classification code to the UI.

---

# 22. Permanent All Apps groups

Three generated groups remain present regardless of other categories:

```text
Recent Files
Recent Texts
Frequent Apps
```

On a five-unit phone layout, the intended form is three `2x2` groups across one row when dimensions allow.

### Recent Files

For the initial implementation, this can bridge to an appropriate Android system document/file surface instead of implementing a file manager.

### Recent Texts

Derived from recent conversation-style notification activity.

This is not direct access to private message databases.

It represents targets inferred from notifications that the launcher has legitimately received.

### Frequent Apps

Shows recently/frequently used applications.

Exclude applications represented in Recent Texts when required by the design.

---

# 23. All Apps list mode

Swiping downward from the categorized All Apps layout switches to a vertically scrolling alphabetical app list.

This is another view over the same app repository.

Rows contain:

* application icon;
* label;
* configured quick actions where applicable.

If a Home tile already has configured quick shortcuts, those can be exposed in the list without creating another separate shortcut configuration.

---

# 24. Global search

Search is not merely app filtering.

Required searchable domains are:

* installed apps;
* app shortcuts;
* contacts;
* available files;
* stored recent notification/message information.

Use independent search providers:

```kotlin
interface SearchSource {
    suspend fun search(query: SearchQuery): List<SearchResult>
}
```

For example:

```text
AppSearchSource
ShortcutSearchSource
ContactSearchSource
FileSearchSource
NotificationSearchSource
```

Then combine/rank the results.

Permission-dependent sources simply become unavailable when their required access is not granted.

---

# 25. Message Center

The Message Center uses a launcher-owned normalized view of notifications.

`NotificationListenerService` is the correct Android entry point for receiving notification posting, removal, and ranking changes. ([Android Developers][4])

Normalize platform objects before UI code consumes them.

```text
Android notification
       ↓
platform adapter
       ↓
LauncherNotification
       ↓
history/classification
       ↓
Message Center / Home tiles / Search
```

---

# 26. Independent notification history

The design requires Message Center history not to vanish solely because the notification shade was cleared.

Therefore store the permitted information received while the notification existed.

For example:

* application;
* timestamp;
* title;
* body;
* conversation identity when available;
* launcher category.

This is launcher history, **not access to the application's underlying message database**.

Old notification actions may become invalid, so historical items must gracefully fall back to opening their source application.

---

# 27. Message Center filters

Required visible chips:

```text
(Priority)
(Conversations)
(Mail)
(dynamic categories...)
(Other)
```

These are the same custom pill-chip family described earlier.

The chip host remains visually invisible.

`Other` contains lower-value notifications rather than deleting them.

Initial classification should prefer transparent rules based on:

* Android notification category;
* application;
* channel;
* conversation status;
* user rules;
* ongoing/silent state.

---

# 28. Priority system

Long pressing a Message Center entry permits creating priority rules for:

* sender/conversation;
* category/channel;
* matching content;
* entire application.

Explicit user rules override automatic classification.

---

# 29. Recent actions

The Message Center also contains recent action circles.

Examples:

* call a recent contact;
* continue a recent conversation;
* use an application shortcut;
* open a recent email target.

Store stable action descriptors and resolve them when selected.

Do not persist arbitrary live `Intent` objects as long-term state.

---

# 30. News Feed

The News Feed implementation should reproduce the **launcher-side feed integration model used by Lawnchair**, rather than building our own feed reader.

The desired user result is:

```text
Home
← swipe →
selected feed provider
```

Supported initial providers:

```text
Off
Lawnfeed
Neo Feed
```

Lawnfeed's stated purpose is to add the Google feed to Lawnchair. ([GitHub][2])

Neo Feed describes itself as a custom Google-Discover-style feed replacement and explicitly lists Lawnchair among compatible launchers supporting custom feed providers. ([GitHub][5])

Current Lawnchair documentation also demonstrates a selectable `Feed provider` model for alternative providers such as Smartspacer. ([GitHub][6])

---

# 31. Feed interaction

The feed must behave like an attached launcher surface.

Required:

```text
finger starts horizontal swipe
↓
feed moves with finger
↓
partial reveal remains interactive animation state
↓
release
├── past threshold → feed opens
└── below threshold → Home restores
```

Not:

```text
swipe
↓
launch external Activity
```

The launcher-side feed adapter must own:

* provider discovery;
* provider connection;
* provider selection;
* binding lifecycle;
* reveal progress;
* settle animation;
* reconnection;
* configuration changes;
* unavailable-provider behavior.

Home should consume only a provider-neutral interface.

---

# 32. Gesture arbitration

Home has several competing gestures.

Priority should be explicit:

```text
active tile drag/resize
        >
folder interaction
        >
widget-owned gesture
        >
explicit vertical Home scroll
        >
horizontal feed reveal
        >
ordinary tap
```

Directional locking and touch slop are required.

Examples:

* vertical Home scrolling must not accidentally reveal the feed;
* horizontally swiping toward the feed must not drag a tile;
* an open folder blocks feed gestures;
* resize mode blocks feed gestures.

---

# 33. Scroll and overlay behavior

The normal Home viewport behaves approximately as:

```text
╭────────────────────────────╮
│                            │
│     scrolling tiles        │
│                            │
│ tiles can pass behind      │
│ floating controls          │
│                            │
│       (filters)            │
│       [ search ]           │
│      ○  ○  ○  ○            │
│    ╭────────────╮          │
│    │○  ○  ○  ○ │          │
│    ╰────────────╯          │
╰────────────────────────────╯
```

The controls remain visually stationary while the grid moves.

The filtered Tetris mode changes only the placement floor:

```text
tiles
tiles
tiles
────────── interactive packing floor
floating controls
```

This distinction must exist in the layout engine itself.

---

# 34. Persistence

Persist at minimum:

```text
HomeLayout
GridPlacement
Tile
Folder
FolderMembership
ShortcutBinding
HostedWidget
FeedPreference
AppCategoryOverride
PriorityRule
NotificationHistoryItem
LaunchUsageRecord
```

A `GridPlacement` must also contain its `homeId`.

Do not accidentally create one global coordinate table shared between both Homes.

---

# 35. Architecture boundaries

Recommended conceptual ownership:

```text
launcher-model/
    grid/
    placement/
    tiles/
    folders/
    categories/
    notifications/
    search/

launcher-data/
    homes/
    preferences/
    notification-history/
    usage/

platform-apps/
platform-shortcuts/
platform-widgets/
platform-notifications/
platform-contacts/
platform-files/
platform-feed/

feature-home/
feature-allapps/
feature-folders/
feature-messagecenter/
feature-search/
feature-settings/

ui-components/
    navigation/
    chips/
    search/
    tiles/
    animation/
```

`ui-components` is important because the navigation, tab chips, and other floating controls are reusable **custom components**, not feature-local copies.

---

# 36. Grid engine responsibilities

The grid engine must own:

* five-column geometry;
* conversion from cell → pixel coordinates;
* collision checking;
* vacant-space checking;
* drag target calculation;
* free placement;
* vertical extent calculation;
* Home 1/Home 2 isolation;
* filtered Tetris packing.

Keep these separate:

```text
FreePlacementEngine
FilteredPackingEngine
```

Do not make filtered Tetris behavior leak into normal Home editing.

---

# 37. Rendering performance

The infinitely extendable Home must not compose every tile between row zero and the furthest occupied item.

Use viewport-aware placement/rendering.

The layout engine knows logical placements; the rendering layer should compose only objects intersecting or approaching the viewport.

The architecture therefore resembles:

```text
persistent placement map
        ↓
spatial/row index
        ↓
visible placement query
        ↓
custom Compose layout
```

Do not represent 500 empty rows with 500 actual spacer rows.

The Home is logically unbounded, not physically preallocated.

---

# 38. Revised implementation order

### Phase 1 — custom visual primitives

Implement:

* navigation pill;
* circular navigation button;
* invisible chip host;
* pill tab chip;
* overlay layer;
* common animation primitives.

### Phase 2 — vertical grid engine

Implement:

* five-column geometry;
* free placement;
* collisions;
* dynamically extending vertical bounds;
* viewport rendering;
* vertical scrolling.

Exit condition:

> Arbitrary rectangles can be freely positioned anywhere in an indefinitely extendable vertical five-column canvas.

### Phase 3 — dual Home layouts

Add:

* Home 1;
* independent Home 2;
* optional Home 2 enablement;
* independent persistence.

### Phase 4 — application tiles

Add:

* 1x1 applications;
* launching;
* drag/drop;
* resizing;
* 2x1/1x2/2x2/4x2 forms.

### Phase 5 — shortcuts

Add proper Android shortcut discovery and invocation.

### Phase 6 — folders

Add five-wide floating folder overlays.

### Phase 7 — widgets

Add real `AppWidgetHost` support.

### Phase 8 — projections and Tetris

Add:

* Show All Apps;
* Show All Widgets;
* pooling from both Homes;
* deterministic bottom-up packing;
* measured interactive bottom boundary.

### Phase 9 — All Apps

Add:

* category grid;
* three permanent generated groups;
* alphabetical swipe-down view.

### Phase 10 — Message Center

Add:

* notification listener;
* persistent launcher history;
* categories;
* priority rules;
* recent actions;
* notification-backed expanded tiles.

### Phase 11 — global search

Add each search provider independently.

### Phase 12 — external News Feed

Reimplement the Lawnchair-style feed host/provider boundary and prove both:

```text
Lawnfeed → Google feed
Neo Feed → third-party/RSS feed
```

can be selected and reached by the same Home swipe.

---

# 39. Final acceptance criteria

A normal phone implementation is complete when:

* Home is **five cells wide**.
* Home is a **single vertically scrolling canvas**, not pages.
* It can extend downward as far as required.
* Tiles remain exactly where the user places them unless there is a collision.
* Home 1 and Home 2 maintain different arrangements.
* All Apps/All Widgets filters pool eligible content from both Homes.
* Filtered content Tetris-packs upward from above the floating controls.
* Normal Home tiles can visibly scroll behind those controls.
* Navigation is a centered floating compact pill containing four circular buttons.
* Navigation geometry uses coordinated/concentric radii.
* Navigation is custom-built.
* Tab chips are custom pill bubbles.
* Their parent positioning container is visually invisible.
* Apps expand from 1x1 into shortcut/information surfaces.
* Large app tiles can become real Android widgets.
* Folders open as floating five-column overlays.
* All Apps begins with grouped tiles, not an ordinary drawer.
* Message Center maintains its own captured history.
* Search spans more than applications.
* Home can horizontally reveal the selected external feed.
* Both Lawnfeed and Neo Feed can operate through the same provider-neutral feed integration.

That now captures the additional layout and interaction constraints that were missing from my first version.

[1]: https://developer.android.com/reference/android/content/pm/LauncherApps?authuser=3&utm_source=chatgpt.com "LauncherApps  |  API reference  |  Android Developers"
[2]: https://github.com/lawnchairlauncher/lawnfeed?utm_source=chatgpt.com "GitHub - LawnchairLauncher/lawnfeed: Adds Google Feed to Lawnchair. · GitHub"
[3]: https://developer.android.com/reference/android/appwidget/AppWidgetHost?utm_source=chatgpt.com "AppWidgetHost  |  API reference  |  Android Developers"
[4]: https://developer.android.com/reference/android/service/notification/NotificationListenerService?utm_source=chatgpt.com "NotificationListenerService  |  API reference  |  Android Developers"
[5]: https://github.com/NeoApplications/Neo-Feed?utm_source=chatgpt.com "GitHub - NeoApplications/Neo-Feed: a custom Google Now Feed alternative for launchers · GitHub"
[6]: https://github.com/LawnchairLauncher/docs/blob/main/help/integrations/smartspacer.md?utm_source=chatgpt.com "docs/help/integrations/smartspacer.md at main · LawnchairLauncher/docs · GitHub"
