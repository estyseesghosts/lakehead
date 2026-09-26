Below is a design specification only. It defines behavior, interaction, layout, state, and constraints. It intentionally does **not** prescribe repository structure or an implementation sequence.

# Launcher Navigation and Alphabetic App List Design Specification

## 1. Scope

This specification adds two related navigation systems:

1. **Alphabetic section navigation inside the all-apps list**
   - Apps are grouped by their first letter.
   - Each group has a visible letter header.
   - Selecting a letter header opens an alphabet jump grid.
   - Selecting a letter in that grid jumps directly to the corresponding app section.

2. **Horizontal launcher navigation**
   - The launcher becomes a three-page horizontal surface.
   - From left to right:

     **Feed Overlay → Tile Grid → All Apps**

   - Horizontal swiping moves continuously between these surfaces.
   - The tile grid is the central/default launcher page.
   - The feed page must support the Launcher3-style feed-overlay model later, even though that integration is not implemented yet.

These systems must feel like parts of one launcher rather than unrelated screens.

---

# 2. Primary Horizontal Structure

The launcher must be treated as a horizontally navigable three-surface workspace.

```text
┌─────────────────┬─────────────────┬─────────────────┐
│                 │                 │                 │
│  Feed Overlay   │    Tile Grid    │    All Apps     │
│                 │                 │                 │
│      Page 0     │      Page 1     │      Page 2     │
│                 │                 │                 │
└─────────────────┴─────────────────┴─────────────────┘
```

The logical order is fixed:

```text
LEFT                                      RIGHT
Feed Overlay  <---->  Tile Grid  <---->  All Apps
```

The **Tile Grid** is the launcher home/default surface.

Launching or returning to the launcher should normally expose this central page unless an existing launcher-state rule deliberately restores another page.

---

# 3. Horizontal Navigation Model

## 3.1 Continuous page movement

Horizontal transitions must be gesture-driven rather than discrete screen replacements.

During a drag:

- the current page moves with the finger;
- the adjacent page becomes visible progressively;
- there must not be a fade-to-new-screen transition;
- there must not be an intermediate blank screen;
- both pages should appear physically adjacent in the horizontal workspace.

Conceptually:

```text
finger moves left
        ↓

┌──────────────┬──────────────┐
│ Tile Grid    │ All Apps     │
│              │              │
└──────────────┴──────────────┘
        ← viewport →
```

The viewport moves through a wider logical surface.

The finished motion should resemble paging between physical panels rather than navigating to another route.

---

## 3.2 Gesture directions

From the **Tile Grid**:

- swipe right → Feed Overlay;
- swipe left → All Apps.

From the **Feed Overlay**:

- swipe left → Tile Grid;
- swiping farther right must not expose another launcher page.

From **All Apps**:

- swipe right → Tile Grid;
- swiping farther left must not expose another launcher page.

---

# 4. Gesture Requirements

The horizontal gesture must support:

- direct finger tracking;
- partial page exposure;
- velocity-based page completion;
- distance-based page completion;
- cancellation back to the current page;
- natural settling animation;
- edge resistance at the first and last page.

Do not implement this as:

```text
swipe detected
→ play canned transition
→ replace screen
```

Instead, page position should track gesture progress.

For conceptual purposes:

```text
horizontalPosition =
    currentPageOffset + dragDistance
```

The UI should be rendered using this continuously changing position.

---

# 5. Horizontal Gesture Thresholds

Exact numerical thresholds may be tuned later.

The expected behavior is:

### Slow drag

If the drag crosses a meaningful fraction of the page width, settle onto the adjacent page.

### Short fast flick

A sufficiently strong horizontal velocity may change pages even if the gesture has not crossed the normal distance threshold.

### Small drag

If neither velocity nor distance reaches the navigation threshold, return to the original page.

There must not be a tiny gesture threshold that causes accidental page changes.

---

# 6. Direction Locking

Vertical scrolling and horizontal page navigation will coexist.

This is especially important on the All Apps page, which is primarily vertically scrolling.

Gesture interpretation must therefore establish an axis.

Example:

```text
mostly vertical movement
→ app-list scroll

mostly horizontal movement
→ launcher page swipe
```

Do not simultaneously:

- scroll the app list vertically;
- and drag the launcher horizontally.

Once the gesture clearly resolves to one axis, that axis should remain locked until the pointer is released.

Use an ordinary touch-slop region before committing.

---

# 7. Diagonal Gestures

Diagonal motion should resolve according to dominant intent.

Example:

```text
Δx = 72 px
Δy = 15 px

→ horizontal page gesture
```

```text
Δx = 19 px
Δy = 84 px

→ vertical app-list scroll
```

Avoid a rigid implementation where any small horizontal movement steals an otherwise vertical scroll.

This is particularly important because users commonly scroll app lists with imperfectly vertical finger movement.

---

# 8. Feed Overlay Surface

The left-hand page is reserved for a Launcher3-compatible feed-overlay experience.

The final architecture must allow this page to host providers such as the launcher feed mechanisms planned elsewhere.

The feed page must therefore not be designed as an ordinary internally-owned launcher page whose only content is hard-coded Compose UI.

Instead, treat it as a **feed surface boundary**.

Conceptually:

```text
Launcher horizontal workspace
        │
        ├── Page 0: Feed host / overlay surface
        ├── Page 1: Launcher tile grid
        └── Page 2: Launcher-owned all-apps UI
```

The feed itself is not part of this implementation milestone.

---

# 9. Feed Page Before Feed Support Exists

The launcher must support the three-page navigation structure before the Launcher3 feed integration exists.

Until a feed provider is available, Page 0 may use a temporary placeholder or disabled state.

However:

- Page 0 must remain structurally present.
- The horizontal page model must not have to be redesigned later.
- The tile grid must already occupy the logical center page.
- Adding the real feed later should mean replacing the contents/host behavior of Page 0, not replacing launcher navigation.

Temporary development presentation may be very simple.

For example:

```text
Feed unavailable
```

or a blank development surface.

Do not treat this placeholder design as final UI.

---

# 10. Feed Gesture Continuity

The transition between feed and tile grid should eventually appear seamless.

Expected interaction:

```text
Feed                           Tile grid
│                                  │
│  ← drag viewport continuously →  │
│                                  │
```

The feed should feel spatially located immediately to the left of Home.

Avoid interactions equivalent to:

```text
swipe right
→ trigger callback
→ launch another Activity
```

The design goal is the traditional integrated launcher feed behavior.

---

# 11. Page State

Each major surface should retain its own local state when the user moves horizontally away from it.

Examples:

### Tile Grid

Preserve:

- vertical scroll position;
- tile arrangement;
- selection/edit state where appropriate.

### All Apps

Preserve:

- vertical scroll position;
- current alphabetic section;
- filter/search state if the launcher later supports it.

### Feed

Feed-specific state should be owned according to the future provider integration.

A horizontal page change must not recreate the entire logical page unnecessarily.

For example:

```text
All Apps at letter M
→ swipe to Home
→ swipe back to All Apps
```

should normally return to approximately the same point in the list.

---

# 12. All Apps List

The All Apps page remains a vertically scrolling alphabetical list.

Its basic structure is:

```text
A
AliExpress
Amazon
Apple Music

B
Bandcamp
Books
Browser

C
Calendar
Camera
Chrome
...
```

Each alphabetic group consists of:

```text
Letter header
App entry
App entry
App entry
...
```

---

# 13. Alphabetic Grouping

Installed apps must be grouped according to the normalized first sortable character of their displayed application label.

Conceptually:

```text
groupKey(appLabel)
```

produces a section key.

Normal Latin alphabet sections are:

```text
A B C D E F G
H I J K L M N
O P Q R S T U
V W X Y Z
```

The visible letter header should correspond to the same key used for ordering and jumping.

---

# 14. Group Header Presentation

The letter header is an interactive element.

Example:

```text
A

AliExpress
Amazon
Apple Music
```

The header is not simply decorative text.

Required behavior:

```text
tap "A"
→ open alphabet jump grid
```

The same action applies to every visible alphabetical header.

---

# 15. Header Visual Role

A group heading should be visually distinguishable from an app.

It should:

- use the section letter as its primary content;
- align consistently with the app-list layout;
- have an adequately large touch target;
- not resemble an application row;
- remain visually lightweight.

The letter itself can remain visually compact while the invisible/interactive touch area is larger.

Example:

```text
┌────────────────────────────────┐
│ A                              │ ← entire header target
└────────────────────────────────┘
```

The user should not need to hit the exact glyph.

---

# 16. Alphabet Jump Grid

Tapping any section header opens a compact alphabet-selection interface.

The required design is based on the Windows 10 Mobile model.

Nominal layout:

```text
4 columns × 7 rows
```

This produces 28 available cells.

The 26 Latin alphabet letters occupy most of them.

Example conceptual arrangement:

```text
A   B   C   D
E   F   G   H
I   J   K   L
M   N   O   P
Q   R   S   T
U   V   W   X
Y   Z   ·   ·
```

The final two cells may remain unused or be used later for non-alphabetic groups.

The important requirement is the **4-column, 7-row visual rhythm**.

---

# 17. Alphabet Grid Behavior

The alphabet grid acts as a navigation index.

Selecting:

```text
M
```

must:

1. dismiss the alphabet grid;
2. find the corresponding M group;
3. move the app list to that section.

Result:

```text
M

Maps
Messages
Music
...
```

The M header should be placed at or near the intended list anchor.

---

# 18. Jump Destination

The jump should align the requested section predictably.

Preferred behavior:

```text
selected section header
→ aligned near the top of the usable app-list viewport
```

It must not merely make the section barely visible at the bottom of the screen.

Example:

Incorrect:

```text
...
L app
L app
                    M
────────────────────── screen bottom
```

Correct:

```text
M
Maps
Messages
Music
...
```

---

# 19. Jump Animation

The alphabet picker represents direct navigation rather than manual scrolling.

The transition therefore does not need to simulate scrolling through dozens of intervening applications.

For a large jump such as:

```text
A → W
```

the preferred result is a short controlled transition or immediate indexed jump.

Do not produce a multi-second animated scroll through the full app list.

A small settling animation is acceptable.

---

# 20. Available and Unavailable Letters

The alphabet grid must communicate which sections actually exist.

Example app collection:

```text
A
B
D
F
M
S
Y
```

Then the picker should distinguish these letters from empty sections.

Conceptually:

```text
A   B   C   D
●   ●   ○   ●
```

Exact styling can be refined later.

Required semantics:

- existing section → active;
- missing section → inactive or disabled.

A letter with no corresponding apps must not navigate to an unrelated section.

---

# 21. Missing Letter Interaction

The preferred initial behavior is:

```text
tap unavailable letter
→ no navigation
```

The letter may either:

- not respond;
- or produce a very subtle disabled-state response.

Do not automatically jump to the next letter.

For example, if there is no `Q`, tapping `Q` must not silently open `R`.

This keeps the alphabet grid deterministic.

---

# 22. Alphabet Grid Modal Model

The alphabet grid is temporary navigation UI, not a fourth launcher page.

It should appear above the All Apps surface.

Conceptually:

```text
┌────────────────────────────────┐
│                                │
│        A   B   C   D           │
│        E   F   G   H           │
│        I   J   K   L           │
│        M   N   O   P           │
│        Q   R   S   T           │
│        U   V   W   X           │
│        Y   Z                   │
│                                │
└────────────────────────────────┘
```

It should not alter the underlying app-list navigation hierarchy.

---

# 23. Picker Appearance

The current milestone does not require polished final styling.

However, the structural requirements are:

- centered or clearly anchored within the app-list viewport;
- 4 equal columns;
- 7 equal logical rows;
- large touch targets;
- consistent cell dimensions;
- no scrolling inside the alphabet picker;
- all alphabet cells visible simultaneously on a normal phone display.

The user must be able to identify and select a letter very quickly.

---

# 24. Background Treatment

When the alphabet picker is open, the underlying list should remain spatially understandable.

Suitable temporary approaches include:

- slight dimming;
- slight opacity reduction;
- simple overlay;
- lightweight modal surface.

Do not navigate to a separate full-screen alphabet Activity or route.

The user should perceive:

```text
I am still inside All Apps.
I have temporarily opened its alphabet navigator.
```

---

# 25. Dismissal

The alphabet picker must close when:

- an active letter is selected;
- the user taps outside it;
- system back is invoked;
- another explicit dismissal action occurs.

After dismissal without selecting a letter, the list position must remain unchanged.

---

# 26. Back Navigation

Expected back behavior:

### Picker open

```text
Back
→ close alphabet picker
```

### All Apps normal state

Back behavior should follow launcher conventions.

Do not require Back to return Home if horizontal navigation is intended to be the primary page model unless the launcher already defines that behavior elsewhere.

The horizontal page state and Android Back stack should not be implemented as if every horizontal page were a separate Activity or navigation destination.

---

# 27. Alphabet Section Data Model

The UI should conceptually operate on grouped data rather than infer positions from rendered pixels.

Example conceptual structure:

```text
AppSection(
    key = "A",
    apps = [...]
)

AppSection(
    key = "B",
    apps = [...]
)
```

The index navigator should map:

```text
"A" → section A
"B" → section B
...
```

Do not implement alphabet jumps by:

- estimating pixel positions;
- assuming each app row has identical cumulative offsets;
- searching rendered UI nodes.

The list's logical dataset should know where each section begins.

---

# 28. Section Stability

Section keys should remain stable when:

- apps are added;
- apps are removed;
- labels change;
- app metadata refreshes.

After a package change, the grouped app dataset and section-index mapping must be recalculated.

Do not retain stale list indices after the underlying app collection changes.

---

# 29. Sorting

Applications inside a section should use the same normalized sorting rules as the full application list.

The navigation index must be based on that same ordering system.

Avoid:

```text
display grouping uses one collation rule
jump index uses another
```

because this can create incorrect section targets.

---

# 30. Non-Latin and Non-Alphabetic Names

The initial Latin alphabet UI must not force every application into A–Z incorrectly.

The data model should allow additional section types later.

Possible examples:

```text
#
あ
ア
한
中
```

The initial visible picker can remain based on the 4 × 7 Windows-style Latin layout, but section handling should not permanently assume:

```text
section key == ASCII A..Z
```

A fallback non-alphabetic section such as:

```text
#
```

may be required for:

- digits;
- punctuation;
- symbols;
- otherwise unclassified labels.

Exact internationalized picker behavior can be specified separately.

---

# 31. All Apps Scrolling

All Apps remains vertically scrollable independently of horizontal workspace movement.

The list should support:

- normal drag scrolling;
- fling scrolling;
- direct alphabet jump;
- preservation of scroll position.

The section headers move with the content unless a separate sticky-header design is explicitly introduced later.

Do not make them sticky by assumption.

The Windows-inspired requirement described here concerns **group headers and alphabet jumping**, not necessarily sticky headers.

---

# 32. Interaction Between Alphabet Picker and Horizontal Paging

While the alphabet picker is open, horizontal launcher paging should be temporarily blocked.

Reason:

```text
picker is open
→ user gesture should interact with picker/modal state
→ not accidentally swipe entire launcher away
```

The modal layer has interaction priority.

After it closes, normal horizontal navigation resumes.

---

# 33. Interaction Between App Rows and Paging

Horizontal navigation must not make app interactions unreliable.

Examples:

- tap app → launch app;
- vertical drag → scroll list;
- clear horizontal drag → page toward tile grid.

A small horizontal component during an ordinary tap must not turn the gesture into a page swipe.

---

# 34. Touch Ownership

A useful conceptual hierarchy is:

```text
Pointer Down
    │
    ├── modal alphabet picker active?
    │      └── picker owns gesture
    │
    └── normal launcher
           │
           ├── movement remains under touch slop
           │      └── undecided
           │
           ├── vertical intent
           │      └── current page content owns gesture
           │
           └── horizontal intent
                  └── launcher pager owns gesture
```

This distinction is important.

Do not let every nested component independently react to the same pointer movement.

---

# 35. Tile Grid Relationship

The tile grid remains the central launcher experience.

Nothing in this change should alter:

- free tile placement;
- tile sizing;
- tile interactions;
- tile scrolling;
- existing tile layout data.

The only new top-level behavior is that the entire tile-grid surface now has adjacent horizontal surfaces.

Conceptually:

```text
horizontal pager
└── tile page
    └── existing vertically scrolling tile grid
```

---

# 36. Vertical Tile Scrolling

The existing vertically scrolling tile grid must continue to work.

This creates the same axis-arbitration issue as All Apps.

On Home:

```text
vertical drag
→ scroll tile canvas

horizontal drag
→ expose feed or All Apps
```

The horizontal workspace must therefore not consume every pointer immediately.

---

# 37. Grid Position Preservation

Example expected behavior:

```text
Home grid scrolled far downward
→ swipe left
→ browse All Apps
→ swipe right
```

The user should return to the same Home-grid vertical position.

Horizontal pages represent persistent launcher surfaces, not screens rebuilt from their initial state every time.

---

# 38. Page Composition

The conceptual UI hierarchy should resemble:

```text
LauncherRoot
│
└── HorizontalWorkspace
    │
    ├── FeedSurface
    │
    ├── TileGridSurface
    │   └── VerticalTileGrid
    │
    └── AllAppsSurface
        ├── VerticalAppList
        │   ├── SectionHeader("A")
        │   ├── App(...)
        │   ├── ...
        │   ├── SectionHeader("B")
        │   └── ...
        │
        └── AlphabetPickerOverlay
```

This diagram expresses design ownership only.

The implementation-planning agent should map it onto the existing codebase rather than copy this hierarchy literally.

---

# 39. Avoid Route-Based Mental Model

The three launcher pages should not behave like:

```text
Navigation route /feed
Navigation route /home
Navigation route /apps
```

with ordinary application screen transitions.

They are better understood as simultaneously adjacent launcher surfaces.

This distinction affects:

- gesture behavior;
- state preservation;
- rendering;
- transition continuity;
- future feed-overlay integration.

---

# 40. Visual Continuity Between Pages

During horizontal dragging, page backgrounds must not cause obvious seams unless intentionally designed.

If the launcher uses wallpaper behind its UI, the wallpaper should continue to behave consistently.

Preferred conceptual model:

```text
persistent launcher/wallpaper environment
        ↓
three horizontally moving foreground surfaces
```

Avoid having each page look like a separate opaque Activity.

The final feed implementation may impose additional constraints on this.

---

# 41. System Insets

All three pages must use the same launcher coordinate system and system-inset policy.

A horizontal swipe must not visibly cause content to jump because one page has different assumptions about:

- status-bar inset;
- navigation-bar inset;
- gesture inset;
- immersive mode;
- display cutout.

The pages should line up geometrically.

---

# 42. Accessibility

Letter headers must expose semantics equivalent to:

```text
A, application section
```

and clearly indicate that they are actionable.

Alphabet-grid buttons should expose:

```text
Jump to A
Jump to B
Jump to C
...
```

Unavailable sections should expose a disabled state.

The alphabet picker must support focus navigation independently of touch.

---

# 43. Dynamic Changes

If the installed-app collection changes while All Apps is open:

```text
package install
package removal
package label change
profile availability change
```

the launcher should:

1. refresh the sorted app set;
2. regenerate alphabet sections;
3. regenerate the set of enabled alphabet picker entries;
4. preserve current list context when reasonably possible.

For example, if the user is viewing `M`, updating another section should not unnecessarily reset them to `A`.

---

# 44. Empty All Apps State

If no launchable apps exist in the active app dataset:

- display the existing launcher empty state;
- alphabet headers should not exist;
- the alphabet picker should not be openable.

Do not render an alphabet grid with every letter disabled.

---

# 45. Single-Section State

If every app belongs to one section, the section header remains valid and selectable.

Selecting it may still open the alphabet picker for consistency.

This behavior can later be optimized if user testing shows the picker is unnecessary in this case.

---

# 46. Search Interaction

If All Apps later gains search, alphabet navigation must be treated as navigation for the **normal grouped app-list mode**.

During search results:

```text
search query active
→ alphabetical section navigation may be hidden or disabled
```

Do not attempt to jump into section offsets belonging to the unfiltered dataset while displaying filtered results.

The implementation should keep these concepts separable even if search is not part of the current milestone.

---

# 47. App Filters

The same principle applies to any launcher-level filters.

If filtering changes the visible All Apps dataset, the alphabet index should represent the currently visible grouped dataset.

Example:

```text
visible apps:
A
M
S

picker:
A active
M active
S active
all others inactive
```

It must not navigate to hidden applications.

---

# 48. Animation Principles

Motion should communicate physical structure.

### Horizontal workspace

Use:

- translation;
- direct gesture tracking;
- natural settling.

Avoid:

- cross-fade;
- scale-out/scale-in;
- navigation-route animation.

### Alphabet picker

Use a short appearance/dismissal animation.

Potential behavior:

```text
small scale + opacity
```

or

```text
simple fade
```

The specific animation is secondary to responsive interaction.

### Alphabet jump

Use either:

- direct indexed movement;
- or a short scroll.

Avoid visually traversing the entire list.

---

# 49. Performance Requirements

Horizontal paging must remain responsive even when:

- the tile grid contains many items;
- All Apps contains hundreds of applications;
- All Apps is at an arbitrary scroll position;
- the feed integration eventually contains complex content.

The design should avoid requiring all expensive child contents to recompose or recalculate continuously simply because page offset changes.

Similarly, opening the alphabet picker should not require rebuilding the entire app dataset.

The implementation-planning agent should specifically inspect recomposition boundaries and state ownership.

---

# 50. No Pager-Induced State Destruction

Avoid designs equivalent to:

```text
if page == HOME:
    create Home()

if page == APPS:
    destroy Home()
    create Apps()
```

The user-visible pages should behave as persistent surfaces.

Whether all pages remain physically composed at all times is an implementation decision, but state must survive page movement.

---

# 51. Input Priority Summary

Interaction priority should be approximately:

```text
1. Open modal UI
   └── alphabet picker

2. Explicit child interaction
   └── app tap, tile action, etc.

3. Gesture direction resolution

4A. Horizontal gesture
    └── workspace paging

4B. Vertical gesture
    └── current page scrolling
```

Gesture ownership must be resolved deliberately rather than relying on accidental nested-scroll behavior.

---

# 52. Required User Flows

## Flow A — Home to All Apps

```text
User is on Tile Grid
→ swipes left
→ All Apps enters from right
→ finger directly controls transition
→ launcher settles on All Apps
```

---

## Flow B — All Apps to Home

```text
User is in All Apps
→ swipes right
→ Tile Grid enters from left
→ previous Home scroll position is preserved
```

---

## Flow C — Home to Feed

```text
User is on Tile Grid
→ swipes right
→ Feed surface enters from left
```

Until real feed support exists, the temporary feed surface appears instead.

---

## Flow D — Alphabet Navigation

```text
User scrolls to section C
→ taps C header
→ alphabet picker opens
→ taps S
→ picker closes
→ list jumps to S
```

---

## Flow E — Cancel Alphabet Picker

```text
User taps M header
→ picker opens
→ taps outside picker
→ picker closes
→ list remains at previous position
```

---

## Flow F — Missing Letter

```text
No installed applications begin with Q

→ user opens alphabet picker
→ Q appears unavailable
→ tapping Q performs no list navigation
```

---

## Flow G — Vertical Scroll

```text
User drags upward through All Apps
→ gesture resolves vertically
→ list scrolls
→ horizontal workspace remains fixed
```

---

## Flow H — Horizontal Drag From All Apps

```text
User begins a clearly horizontal drag to the right
→ All Apps moves with finger
→ Tile Grid is exposed
→ drag passes threshold
→ launcher settles on Tile Grid
```

---

# 53. Acceptance Criteria

The feature is functionally complete when all of the following are true:

### Horizontal workspace

- There are three logical launcher pages.
- Their order is Feed → Tile Grid → All Apps.
- Tile Grid is the central/default launcher page.
- Pages can be moved between using horizontal swipes.
- Page movement follows the pointer continuously.
- Home and All Apps vertical scrolling remains functional.
- Gesture direction is reliably disambiguated.
- Page-local scroll state survives page changes.
- Edge swipes do not expose nonexistent fourth pages.
- The left feed surface can initially use temporary content.
- The navigation design does not block later Launcher3 feed-overlay integration.

### All Apps grouping

- Apps appear in alphabetical groups.
- Each group displays a letter header.
- Header ordering matches app ordering.
- The header is interactive.

### Alphabet picker

- Tapping any alphabetic header opens the picker.
- The picker uses a four-column, seven-row grid structure.
- A–Z are represented.
- Letters with visible sections are active.
- Letters without sections are visibly inactive.
- Selecting an active letter closes the picker and jumps to that section.
- Selecting an unavailable letter does not jump elsewhere.
- Back dismisses the picker.
- Tapping outside dismisses the picker.
- Dismissing without selection preserves list position.

### State and data

- Alphabet targets derive from the app dataset rather than pixel estimation.
- Package changes regenerate sections correctly.
- Horizontal page movement does not reset list positions.
- Opening the alphabet picker does not recreate the app list.
- Horizontal paging is disabled while the picker is open.

---

# 54. Explicit Non-Goals for This Change

Do **not** expand this milestone into:

- final Lawnfeed integration;
- final Neo Feed integration;
- feed-provider discovery;
- feed configuration UI;
- redesign of tile visuals;
- redesign of app rows;
- app-list search implementation;
- sticky section headers;
- final internationalized alphabet picker;
- final animation polish;
- app categorization;
- Windows Phone visual replication.

Those can build on this architecture later.

---

# 55. Design Intent

The final interaction model should be understandable as one spatial launcher:

```text
                    horizontal space

  external feed         home             application index
       │                 │                       │
       ▼                 ▼                       ▼

┌──────────────┐ ┌──────────────┐ ┌────────────────────┐
│              │ │              │ │ A                  │
│     FEED     │ │  TILE GRID   │ │ App                │
│              │ │              │ │ App                │
│              │ │              │ │                    │
│              │ │              │ │ B                  │
│              │ │              │ │ App                │
└──────────────┘ └──────────────┘ └────────────────────┘
       ↔                 ↔

                                     tap section header
                                             │
                                             ▼

                                  ┌────────────────────┐
                                  │ A   B   C   D      │
                                  │ E   F   G   H      │
                                  │ I   J   K   L      │
                                  │ M   N   O   P      │
                                  │ Q   R   S   T      │
                                  │ U   V   W   X      │
                                  │ Y   Z              │
                                  └────────────────────┘
```

The important architectural idea is that **horizontal movement handles major launcher surfaces, while alphabet navigation handles large vertical movement inside All Apps**.

These are complementary navigation systems:

```text
Horizontal:
Feed ↔ Home ↔ Apps

Vertical:
Tile grid scrolling
All Apps scrolling

Indexed:
App section header → alphabet picker → direct section jump
```

The implementation plan should preserve these three distinct navigation roles rather than merge them into one generic navigation system.

The implementation-planning agent should use this as the behavioral source of truth, then inspect the existing pager/navigation, tile-grid scrolling, all-apps data model, and gesture handling before choosing Compose primitives or module boundaries.
