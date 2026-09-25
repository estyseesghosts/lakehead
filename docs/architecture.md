# Architecture and ownership

The included Gradle modules are real boundaries for the first milestone. Additional owners listed below become modules when implementation needs them; do not create empty modules solely to populate the tree.

| Module | Ownership |
| --- | --- |
| `:fixtures:launchable` | Source-free Android test infrastructure: isolated installable launchable-activity fixture for real package add/remove validation; no production module depends on it |
| `:app` | Android application entry point and composition wiring; no platform catalogue or grid logic |
| `:core:model` | Stable domain identities and launcher item models; no Android service objects or Compose types |
| `:core:grid` | Sole owner of placement legality, extent, projection, collision, movement, deterministic packing and viewport/count adaptation; no item rendering |
| `:core:designsystem` | Reusable custom Compose launcher primitives and small reusable geometry/layout contracts; no repository access or screen-specific arrangement |
| `:platform:launcherapps` | Profile-aware Android `LauncherApps` inventory, callbacks and launch APIs; translates Android types to domain |
| `:feature:home` | Home screen UI state, gestures, screen-specific arrangement and presentation; depends on grid rather than implementing placement |

Dependency direction: `app → feature → core` and `app → platform → core`; `core:grid → core:model`. Platform implementations never enter `core:model` or Compose state. Feature-to-feature implementation dependencies are prohibited. Application wiring joins feature contracts to platform/data implementations. Each state stream has one owner: installed-app inventory in platform, persisted arrangement in data, transient gesture state in feature, and placement rules in grid.

Design-system primitives describe reusable geometry and small layout contracts; feature screens own arrangement. A screen can place navigation, controls, overlays and other elements through explicit anchors/regions/insets and measured geometry (such as bottom, top, side or floating), without assuming fixed coordinates or a bottom navigation bar. The six-column phone layout is a normal-phone reference arrangement, not a fixed screen contract; arrangement and grid width are inputs that can vary across screen shapes while stable logical content is retained. These contracts must not implement another placement or packing system: `:core:grid` remains the sole owner of placement legality, extent and projection. B3 remains pure count adaptation.

Planned ownership: `core:navigation` for destination state; `data:database` for pages, placements, folders, rules and history; `data:preferences` for settings; `data:repository` for domain-facing data operations; `platform:shortcuts`, `widgets`, `notifications`, `usage`, `contacts`, `documents` each own their Android gateway; `feature:folders`, `allapps`, `search`, `messages`, `editing`, `settings`, `news` own their UI/use cases. `benchmark` owns startup measurements; `build-logic` owns conventions once repetition justifies it.

Decomposition triggers: notification ingestion → parsing → conversation detection → normalization → storage → classification; search query fan-out → individual providers → result ranking; grid geometry → packing → edit validation; app catalogue enumeration → cache → callback invalidation → launching. Keep these separate responsibilities even before separate modules become worthwhile. Files are split for ownership or dependencies, never merely for length.

Implementation order: 0A foundation, 0B HOME/`LauncherApps`, 1A grid engine/tests, 1B custom components, 1C persisted home/editing, 1D folders. See `docs/agents/tasks/foundation.md` for progress.
