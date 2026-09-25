# Android Launcher — Initial Execution Plan

This plan should be treated as the initial implementation roadmap. The sketches define the interaction model, not exact visual styling.

The launcher must be designed as a real launcher platform from the start, not as one large Compose application that is split apart later.

## 1. Non-negotiable architecture rules

1. **Kotlin + Jetpack Compose** for launcher-owned UI.
2. Use **custom UI components from the start**. Do not build the product around Material `NavigationBar`, `Card`, `FloatingActionButton`, `SearchBar`, default chips, or similar high-level components and plan to replace them later.

   * Compose Foundation, animation, gesture, layout, text and graphics APIs are fine.
   * Our own components own appearance, geometry and interaction.
3. On an average non-folding phone:

   * home grid = **6 × 1x1 cells wide**
   * folder grid = **6 × 1x1 cells wide**
   * 1x1 cells are square unless a later design rule explicitly changes this.
4. Layout must support spans from the start:

   * `1x1`
   * `2x1`
   * `1x2`
   * `2x2`
   * `4x2`
   * future sizes without changing the persistence model.
5. Separate **grid placement** from **tile rendering**.
6. Separate **Android platform integration** from Compose UI.
7. No screen composable, repository, service, ViewModel, manager or utility file may become the dumping ground for unrelated responsibilities.
8. Refactoring must have a responsibility or dependency reason. File length alone is not a reason to split code.
9. All important state must have one clear owner.
10. Every phase ends with build, test and architecture validation. Agents must run Gradle validation themselves.

---

# 2. Repository structure

Do not begin with everything under `app/src/main/java/...`.

Start approximately here:

```text
launcher/
├── app/
│
├── core/
│   ├── model/
│   ├── designsystem/
│   ├── grid/
│   ├── navigation/
│   └── common/
│
├── data/
│   ├── database/
│   ├── preferences/
│   └── repository/
│
├── platform/
│   ├── launcherapps/
│   ├── shortcuts/
│   ├── widgets/
│   ├── notifications/
│   ├── usage/
│   ├── contacts/
│   └── documents/
│
├── feature/
│   ├── home/
│   ├── folders/
│   ├── allapps/
│   ├── search/
│   ├── messages/
│   ├── editing/
│   ├── settings/
│   └── news/
│
├── benchmark/
├── build-logic/
│
├── tools/
│   └── scripts/
│
└── docs/
    ├── architecture.md
    ├── design.md
    ├── platform-capabilities.md
    ├── agents.md
    └── agents/tasks/
```

This is also a **decomposition map**. We do not need to create every tiny Gradle module immediately, but these ownership boundaries must exist from day one. If, for example, `platform:notifications` grows into notification ingestion, conversation extraction and ranking, those are known future split points rather than reasons to put everything in `NotificationRepository.kt`.

`core:grid` is especially important. Home, folders, "show all apps", widgets and editing will all depend on it. None of those features should implement their own coordinate/packing system.

---

# 3. Core domain model

Do this before substantial UI work.

The basic identity for an installed app must include **user/profile identity**, not only a package name. `LauncherApps` is explicitly designed around applications across accessible user profiles. ([Android Developers][1])

Core concepts should include roughly:

```text
AppKey
    component
    user/profile

GridPosition
    column
    row

GridSpan
    width
    height

LauncherItem
    AppTile
    ShortcutTile
    FolderTile
    WidgetTile
    BuiltInTile

HomePage
Folder
FolderMember
AppShortcut
NotificationRecord
Conversation
PriorityRule
RecentAction
SearchResult
```

`LauncherItem` placement must not contain Compose state or Android framework objects.

Persist identifiers. Resolve icons, labels, `Drawable`s, `PendingIntent`s, widget views and other Android resources at the platform boundary.

---

# 4. Phase 0 — Governance, repository and agent tooling

Do this before feature implementation.

### Documentation

Create:

* `docs/design.md`

  * transcribe these sketches into explicit behavior.
  * record the six-column rule.
  * record tile sizes.
  * record navigation.
  * record folder behavior.
  * record expansion/widget behavior.
* `docs/architecture.md`

  * module ownership.
  * dependency directions.
  * state ownership.
  * Android/Compose boundary.
* `docs/platform-capabilities.md`

  * permissions.
  * launcher APIs.
  * notification access.
  * widgets.
  * shortcuts.
  * contacts.
  * storage restrictions.
* `docs/agents.md`

  * engineering rules.
* `docs/agents/tasks/`

  * persistent task-state documents for longer work.

### `agents.md`

Explicitly state:

> Do not solve architectural problems by concentrating responsibility into a single file, class, composable, ViewModel, repository, manager or function.

Also require:

* inspect existing architecture before edits;
* preserve module boundaries;
* no generic `Utils.kt`;
* no feature-to-feature implementation dependencies;
* no database access directly from composables;
* no Android service objects in UI state;
* no business logic hidden in composables;
* no large composables that both obtain state and implement complex layout;
* no refactors based only on LOC;
* each change must explain its ownership;
* run required Gradle validation directly;
* do not report validation as impossible when the agent has Gradle permission;
* update task-state documents for multi-slice work.

### Development scripts

Provide these immediately:

```text
repo_map.py
module_graph.py
trace_symbol.py
function_audit.py
compose_audit.py
adb_capture.py
launcher_smoke.py
```

`function_audit.py` should identify unusually large/high-complexity functions.

`compose_audit.py` should separately inspect composables for excessive size, parameters, effects, state ownership and nested UI responsibility.

These are **review signals**, not automatic demands to split files.

`adb_capture.py` should make screenshots/device-state capture fast enough that agents can use it repeatedly.

---

# 5. OpenCode agent structure

Use a bounded agent system rather than giving every agent unrestricted responsibility.

### `orchestrator`

Owns:

* reading current task state;
* determining the correct slice;
* delegating work;
* requesting review;
* ensuring validation happens.

It should not casually implement unrelated code itself.

### `codebase_explorer`

Read-only.

Uses:

```text
repo_map.py
module_graph.py
trace_symbol.py
function_audit.py
compose_audit.py
```

Returns exact ownership and dependency information.

### `architecture_reviewer`

Read-only.

Checks:

* wrong dependencies;
* mixed responsibilities;
* state ownership;
* future scaling problems;
* shortcuts taken against `architecture.md`.

### `android_platform`

Owns Android-specific integration:

* HOME role;
* `LauncherApps`;
* shortcuts;
* profiles;
* widgets;
* notification listener;
* intents;
* permissions.

### `ui_implementer`

Owns:

* custom Compose components;
* grid presentation;
* animations;
* gestures;
* adaptive UI;
* accessibility.

It does not implement Android service/data plumbing.

### `targeted_fixer`

Handles narrowly scoped defects and small refactors.

It **must be allowed to run Gradle builds/tests itself**.

### `git_handler`

Owns Git operations only.

This keeps the orchestration pattern similar to the other projects while adding launcher-specific platform/UI specialization.

---

# 6. Phase 1 — Become a real launcher

Before building sophisticated tiles, establish the platform shell.

Implement:

* HOME activity and correct intent filters;
* role/default-launcher onboarding;
* installed-app discovery;
* app launch;
* package-change callbacks;
* profile-aware app identity;
* basic persistent settings;
* empty custom launcher shell.

Android provides `ROLE_HOME` through `RoleManager` on API 29+. ([Android Developers][2])

Use `LauncherApps.getActivityList()` as the primary launcher-facing application inventory API rather than treating `PackageManager` queries as the complete launcher abstraction. ([Android Developers][1])

**Exit gate:** The launcher can become HOME, display installed applications and reliably launch them. Package installation/removal updates the inventory without restarting.

---

# 7. Phase 2 — Build the grid engine before the home screen

`core:grid` gets implemented independently.

For normal phones:

```text
columns = 6

cellWidth =
    (availableWidth - horizontalPadding - totalColumnGaps) / 6
```

A `2x1`, for example, occupies two cells plus the internal gap. Do not compute every tile size separately.

The grid engine owns:

* collision checks;
* legal spans;
* placement;
* movement;
* resize validation;
* deterministic packing;
* bottom-gravity packing used by filtered modes;
* viewport adaptation.

It does **not** know what an application, folder or widget is.

Write extensive unit tests before adding drag-and-drop.

Required cases include edges, collisions, holes, span changes, rotation/width change and deterministic packing.

---

# 8. Phase 3 — Custom component foundation

Create launcher primitives before complete screens.

Initial components should include:

```text
LauncherTileFrame
AppTile
ShortcutTile
FolderTile
WidgetFrame
LauncherIcon
LauncherNavPill
LauncherNavButton
LauncherSearchField
LauncherFilterButton
LauncherQuickAction
NotificationSummary
FolderOverlay
ResizeHandles
GridDragPreview
```

Each component gets a small public state/event contract.

For example:

```kotlin
AppTile(
    state: AppTileState,
    onAction: (AppTileAction) -> Unit,
)
```

Not:

```kotlin
AppTile(
    repository,
    packageManager,
    navController,
    context,
    database,
    ...
)
```

This is where the project avoids the UI architecture problems encountered in earlier Compose work.

---

# 9. Phase 4 — Home screen

Implement the first usable launcher surface.

### Home behavior

Default application:

```text
1x1
```

Expansion:

```text
1x1 -> application
2x1 / 1x2 -> app + quick shortcuts
2x2 -> shortcuts + notification summary
4x2 -> richer app surface / notification summary
```

Do not make a larger tile equivalent to an Android widget. A launcher tile and an `AppWidget` are separate concepts.

A larger custom tile can later be replaced by the application's real widget where appropriate.

### Editing

Long press enters edit mode.

Edit mode provides:

* movement;
* resizing;
* remove from home;
* create/add to folder;
* replace with widget where supported.

The placement engine decides legality. The composable only manages gesture presentation.

### Secondary home page

Design persistence for multiple home layouts now.

Only Home 1 is enabled initially.

Home 2 can be added later without database migration gymnastics.

---

# 10. Phase 5 — Folders

Folders use the same underlying grid concepts.

On a normal phone, an opened folder is:

* a floating overlay;
* over the current page;
* **six 1x1 cells wide**;
* independently scrollable when required;
* dismissible without leaving the current screen.

Do not implement folders as navigation to another full-screen destination.

Manual home grouping becomes useful metadata elsewhere in the launcher.

---

# 11. Phase 6 — All Apps

The default All Apps surface should not be a conventional Launcher3 alphabetical drawer.

Its primary view is the grouped tile view shown in the sketches.

The grouping resolver should use:

1. explicit user grouping where available;
2. reliable Android application category information;
3. deterministic launcher classification;
4. `Other` as fallback.

Do not scrape Play Store pages or implement unpredictable label-based AI categorization just to avoid `Other`.

Three permanent groups remain visible regardless of automatic grouping:

```text
Recent Files
Recent Messages
Recent / Frequent Apps
```

### Recent Files

For the first implementation, this is a gateway into Android's system document UI rather than pretending the launcher has unrestricted filesystem visibility.

The Storage Access Framework gives an app access to documents or directories selected by the user; `ACTION_OPEN_DOCUMENT` invokes the system picker. ([Android Developers][3])

Real file indexing can come later behind explicit SAF grants.

### Recent/Frequent Apps

Maintain the launcher's own local launch history from the start.

Optional usage-access integration can later enrich this. `UsageStatsManager` generally requires usage-access privileges for historical device usage. ([Android Developers][4])

Exclude applications already represented by Recent Messages when constructing this permanent group.

### Alternate list mode

A vertical gesture transitions from grouped tiles into the compact application list shown in the sketch.

The list can expose an application's shortcuts inline.

`LauncherApps` can query launcher shortcuts when the launcher has shortcut-host permission. ([Android Developers][1])

---

# 12. Phase 7 — Global search

Search must be an aggregation system, not one giant repository query.

Define:

```kotlin
interface SearchProvider {
    suspend fun search(query: SearchQuery): List<SearchResult>
}
```

Initial providers:

```text
Apps
App shortcuts
Notification/message history
Contacts
Launcher actions
```

Later:

```text
User-authorized documents
Settings/actions
Additional data sources
```

Contact search is permission gated because the Contacts Provider requires `READ_CONTACTS` for reading contact data. ([Android Developers][5])

Search UI merges results but does not know how each provider retrieves them.

---

# 13. Phase 8 — Message Center

Treat this as a significant subsystem, not an alternate notification shade.

The launcher maintains its **own history database**.

Use `NotificationListenerService` to receive notification post/removal/ranking events. It requires explicit notification-listener access and can provide current active notifications. ([Android Developers][6])

Do not design around obtaining Android's complete historical notification database.

Store the relevant normalized information when notifications arrive.

Pipeline:

```text
NotificationListenerService
        ↓
Notification parser
        ↓
Conversation detector
        ↓
Normalizer
        ↓
History repository
        ↓
Classifier
        ↓
Message Center UI
```

Filters from the sketch:

```text
Priority
Conversations
Email
Dynamic categories
Other
```

Less useful/noisy items can go into `Other`, but the first classifier should be deterministic and explainable.

Long-pressing an item can create a `PriorityRule`.

Support rule scopes such as:

```text
app
channel/category
conversation
sender
content pattern
```

Keep rule evaluation outside the UI.

---

# 14. Recent actions

The row shown below the Message Center filters should use a provider architecture:

```text
NotificationActionProvider
ShortcutActionProvider
RecentLaunchActionProvider
FutureCallProvider
FutureMailProvider
```

This makes examples such as:

* recent conversation;
* call someone;
* app-specific shortcut;
* open an email;

possible without turning Message Center into a giant platform manager.

---

# 15. Phase 9 — Android widgets

Only after custom tiles and placement are stable should real widgets be hosted.

Android launcher replacements can host widgets through `AppWidgetHost`; the launcher is responsible for binding, widget IDs, sizing and configuration. ([Android Developers][7])

System widgets remain `AppWidgetHostView`/`RemoteViews` based, so Compose will need a clean View interoperability boundary rather than trying to rewrite third-party widgets as Compose. ([Android Developers][8])

Keep:

```text
CustomLauncherTile
```

and:

```text
SystemAppWidgetTile
```

as separate implementations of a common grid item concept.

---

# 16. Global navigation

Implement the four custom destinations shown in the design:

```text
News Feed
Message Center
Home Grid
All Apps
```

Use a custom floating navigation component.

`News Feed` should initially remain a shell/feature interface until its actual data source and behavior are specified. Do not invent a social/news backend merely to fill the tab.

The Home screen can independently expose the small frequently-used-app row from the design.

---

# 17. Foldables and larger displays

Do not implement this by simply increasing every tile.

The six-column rule defines the normal phone layout.

The grid engine must nevertheless accept a variable column count so larger devices can eventually use:

```text
6 phone
>6 wide phone/foldable/tablet
```

The stored layout should be based on logical grid placement rather than pixels.

For initially unsupported widths, deterministic reflow is preferable to corrupting or permanently rewriting the user's phone layout.

---

# 18. Performance rules from the start

Do not allow:

* installed-app enumeration during recomposition;
* icon decoding on every tile composition;
* database queries directly from individual tiles;
* category calculation during every frame;
* synchronous notification parsing in UI;
* widget discovery inside composables;
* global search to block on every provider;
* the complete launcher state to live in one ViewModel.

Cache immutable application metadata where appropriate and invalidate it from launcher/package callbacks.

Maintain stable IDs.

Use lazy composition only where it actually preserves launcher semantics; the custom home grid may need its own layout instead of forcing the design through `LazyVerticalGrid`.

Add macrobenchmark/startup testing before the project becomes large.

---

# 19. Validation for every implementation slice

Every agent slice should finish with:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

plus the relevant module/instrumentation tests.

Then run:

```text
repo_map.py
module_graph.py
function_audit.py
compose_audit.py
```

The review step must answer:

* Did responsibility move to the correct module?
* Did the change introduce a new dependency direction?
* Did a ViewModel/repository/composable accumulate unrelated work?
* Was a platform API leaked into domain/UI state?
* Were tests added for new nontrivial behavior?
* Did the implementation follow the design rather than approximating it with convenient stock Compose controls?

---

# First implementation milestone

The first milestone should **not** try to implement Message Center, search and widgets simultaneously.

Build, in this exact order:

**Slice 0A — Foundation:** repository/module skeleton, documentation, `agents.md`, OpenCode agents, repository-analysis scripts, CI/Gradle validation.

**Slice 0B — Launcher platform:** HOME role, `LauncherApps`, profile-aware installed-app catalogue, app launching and package updates.

**Slice 1A — Grid primitive:** deterministic six-column layout engine, spans and tests.

**Slice 1B — Design system:** custom tile, icon, navigation, search-shell, overlay and editing primitives.

**Slice 1C — Minimal Home:** persisted 1x1 tiles, six columns, app launching, drag/reorder.

**Slice 1D — Folders:** six-column floating folder overlay and persistent membership.

At that point there is a small but genuine launcher with the correct architectural foundation. **Only then** begin expanded tiles, All Apps grouping, global search, Message Center and real Android widgets.

[1]: https://developer.android.com/reference/android/content/pm/LauncherApps?utm_source=chatgpt.com "LauncherApps  |  API reference  |  Android Developers"
[2]: https://developer.android.com/reference/kotlin/android/app/role/RoleManager?utm_source=chatgpt.com "RoleManager  |  API reference  |  Android Developers"
[3]: https://developer.android.com/training/data-storage/shared/documents-files?utm_source=chatgpt.com "Access documents and other files from shared storage  |  App data and files  |  Android Developers"
[4]: https://developer.android.com/reference/kotlin/android/app/usage/UsageStatsManager?utm_source=chatgpt.com "UsageStatsManager  |  API reference  |  Android Developers"
[5]: https://developer.android.com/identity/providers/contacts-provider?utm_source=chatgpt.com "Contacts Provider  |  Identity  |  Android Developers"
[6]: https://developer.android.com/reference/android/service/notification/NotificationListenerService?utm_source=chatgpt.com "NotificationListenerService  |  API reference  |  Android Developers"
[7]: https://developer.android.com/develop/ui/views/appwidgets/host?authuser=160&utm_source=chatgpt.com "Build a widget host  |  Views  |  Android Developers"
[8]: https://developer.android.com/reference/android/appwidget/AppWidgetHostView?authuser=2&utm_source=chatgpt.com "AppWidgetHostView  |  API reference  |  Android Developers"
