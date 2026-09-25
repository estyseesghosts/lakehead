# ProtoLaunch comprehensive implementation plan

This roadmap is the corrected architecture plan produced by agent `three`, reconciled with the newer app-test result. Phase A / 0B, Phase B (B1–B3), Phase C design primitives, Phase D D1–D3 persisted Home rendering, and the navigation shell + flat All Apps + add-to-home slice are complete and device-verified; the next slice is TBD. `docs/grid.md` plus `docs/grid/` photos are the visual/interaction authority for the Home grid, symbols, app list, and hit regions and supersede the older fixed span examples wherever they conflict.

## 1. Verified current baseline

### Implemented

The repository currently has:

- HOME eligibility and a user-triggered `RoleManager.ROLE_HOME` request.
- Framework-free profile-aware identity:
  - `ProfileId`
  - `ComponentIdentity`
  - `AppIdentity`
- `LauncherAppEntry`, pairing an `AppIdentity` with a nonblank display label.
- `LauncherApps` enumeration across accessible profiles.
- Stable profile-token conversion.
- Correct-profile launching through `LauncherApps.startMainActivity`.
- One platform-owned observable inventory in `AppInventory`.
- Conflated inventory refresh and last-known-good retention.
- Package/availability invalidation and API-gated profile lifecycle invalidation.
- Thin `MainActivity` lifecycle wiring for the catalogue.
- App instrumentation under ProtoLaunch’s actual UID.
- Earlier HOME-role and personal-profile launch evidence on the SM-G986W.

### Phase A / 0B gate — complete

- A2 catalogue presentation is complete: five connected feature tests and the app tap-identity instrumentation test passed on API 33; labels, loading and empty states, and click semantics were observed.
- A3 live package mutation is complete: isolated launchable fixture, narrow production `MAIN`/`LAUNCHER` queries, and real install/uninstall visible updates on API 33 with the same Activity instance. The A3 preparation commands remain documented below.
- A4 correct-profile proof is complete on PLG110 (API 36): personal profile 0 and managed work profile 11, shared `com.android.contacts/.DialtactsActivityAlias` distinguished by identity, work launch observed under `u11`, and inaccessible-token failure without a new personal ActivityRecord. Evidence is split between `WorkProfileLaunchProofTest.kt` and `WorkProfileDeviceEvidence.kt`; function audit is clean. On Samsung API 33 without a work profile, the A4 test explicitly skips.
- Full gate: process recreation passed on SM-G986W (PID 5710 → 5797; `MainActivity` resumed and catalogue labels were visible after restart). Samsung TalkBack smoke passed with Samsung TalkBack enabled; UI hierarchy exposed clickable/focusable actions and rows and keyboard focus traversal worked. Speech output was not verified.
- HOME role evidence had shown ProtoLaunch as the sole holder on PLG110 before later test cleanup. Do not infer the current PLG default state from that earlier evidence.
- Samsung connected app suite: 5 tests total, 4 passed and the A4 test skipped (no work profile). Mandatory static suite passed: `assembleDebug`, `test` (29 unit tests), `lint` (0 errors, 31 warnings), `repo_map.py`, `module_graph.py`, `function_audit.py`, and `compose_audit.py`.
- The `appops set ... androidx.test.services` warning was harness-only; product has no external-storage permission.
- A5 settings persistence remains deferred with user approval; it is not a Phase A gate and no speculative settings are to be added.
- Custom launcher design-system components.
- Persisted Home placement.
- Editing.
- Folders.
- All Apps.
- Search.
- Message Center.
- Recent actions.
- Widget hosting.
- Global navigation and gestures.
- News Feed.
- Foldable/wide-screen completion.
- Final accessibility and performance gates.

The user-visible catalogue and live package mutation are implemented; Phase A / 0B is complete. Phase B (B1–B3) is complete. Later phases remain open.

---

# 2. Document precedence and resolved design decisions

## Authority order

When documents disagree, use this order:

1. `AGENTS.md` for workflow, module ownership, and state boundaries.
2. `docs/architecture.md`, `docs/design.md`, `docs/grid.md` (+ `docs/grid/` photos), `docs/navbar.md`, and `docs/platform-capabilities.md` for stable contracts. Where older span examples conflict, `docs/grid.md` governs: spans are generic positive integers, not a closed enum.
3. `docs/first_draft.md` for engineering sequence and milestone gates.
4. `docs/launcher_report.md` for detailed product behavior and target experience.
5. Current source, tests, and device evidence for what actually exists.

The revised launcher report does not justify skipping an unfinished prerequisite. For example, its later surfaces cannot be pulled ahead merely to make the current placeholder look more complete.

## Major resolved decisions

### Two Homes means two vertical canvases

The report’s “two independent Homes” should be represented as two independently keyed vertical layouts, not horizontal pager pages.

Each Home has its own:

- Placements.
- Scroll position.
- Optional enabled state.
- Folders and tile configuration.

A future Home selector can change which `homeId` is active, but Home itself remains one vertically scrolling canvas.

### Normal placement never auto-compacts

Ordinary Home editing preserves holes and unrelated tile positions. Moving one tile cannot silently rearrange the rest of the Home. Empty cells are transparent wallpaper, not placeholders; grid lines are never rendered; tiles never auto-sort, pack toward top-left, require complete rows, or fill empty space (see `docs/grid.md` acceptance criteria 1–8).

Only generated projections such as:

- Show All Apps
- Show All Widgets

may use deterministic bottom-weighted packing. They must use `:core:grid`, not a second packing implementation in a feature or database query.

### Rendering and placement remain separate

Persist:

- Stable item identity.
- `homeId`.
- Logical column and row.
- Span.
- Item-specific logical configuration.

Do not persist:

- Pixels.
- `UserHandle`.
- `Drawable`.
- `PendingIntent`.
- `AppWidgetHostView`.
- Compose state.

`:core:grid` owns legality, geometry, movement, extent, and projection. Compose measures and renders the result.

### Expanded app tiles are not widgets

Custom 2×1, 1×2, 2×2, and 4×2 app tiles remain launcher-owned content, extended by the `docs/grid.md` open span set (3×2, 4×3, 3×3 as valid examples — generic integer dimensions, never a `SMALL`/`MEDIUM`/`LARGE` enum). A real Android widget later uses `AppWidgetHostView` through a separate platform owner.

### Permissioned data is introduced with its feature

Do not add notification, contacts, Usage Access, document indexing, or widget permissions in advance.

In particular:

- Message Center history is notification history captured after the user enables listener access.
- ProtoLaunch cannot reconstruct Android’s complete pre-existing notification history.
- “Recent Texts” means normalized launcher-captured message/conversation records, not direct SMS database access.
- SAF access is limited to user-selected documents/grants.
- Contact access requires its own runtime permission.
- Usage Access is optional enrichment, not a prerequisite for local launch history.

### Feed compatibility must be proven

Lawnfeed and Neo Feed are external providers. Their mention in the report does not prove that ProtoLaunch can discover, bind, authenticate, or consume them.

A provider-neutral internal interface is appropriate, but each external integration requires a real feasibility spike and device proof.

---

# 3. Dependency-safe critical path

The primary sequence is:

```text
Close 0B
  → grid rules
  → custom design primitives
  → persisted Home
  → Home editing
  → folders
  → second Home and expanded tiles
  → filtered projections and global navigation
  → All Apps and locally sourced recents
  → notifications, Message Center, and search
  → widgets
  → feed integration
  → advanced gestures, foldables, performance, and final acceptance
```

Some later slices can run in parallel after their contracts are stable, but feature modules must not depend on one another’s implementations.

---

# Phase A — Close 0B launcher platform — complete

**Outcome:** ProtoLaunch visibly lists installed launchable activities, launches the selected profile-aware identity, and updates after package mutations. Current HOME role selection is owned by Android; no launcher settings persistence is required for Phase A/0B.

**State owners:**

- Installed-app inventory: `:platform:launcherapps`
- Installed metadata resolution: `:platform:launcherapps`
- Transient UI state: `:feature:home`

## A1 — Migrate label-contract test caller — complete

**Owner:** `:app`

The instrumentation test now extracts `LauncherAppEntry.identity` before invoking the launcher.

**Exit evidence:**

- App Android-test Kotlin compilation passed.
- Two app connected tests passed on API 33.

This does not close 0B.

## A2 — Minimal user-visible catalogue presentation — complete

**Device evidence:** On SM-G986W (API 33), ProtoLaunch showed installed launchable activities with visible labels; tapping an entry launched it. This is personal-profile evidence only and does not close A4.

**Owners:**

- Presentation: `:feature:home`
- Thin wiring: `:app`
- No platform state changes

### Contract

`Home` receives:

- `HomeCatalogueState`, with `Loading` before catalogue content is available and `Content(apps: List<LauncherAppEntry>)` afterward (including an empty list for a loaded-empty catalogue).
- `onLaunchApp: (AppIdentity) -> Unit`
- Existing HOME-role action, if still needed

The feature must not access:

- `LauncherApps`
- `Context`
- `UserHandle`
- Platform inventory internals

### UI scope

Build a usable custom list with:

- Visible labels.
- Stable identity retained by each item.
- Accessible click semantics.
- Explicit loading and empty states.
- No stock Material list/card placeholders.
- No grid or persistence yet.

`:app` should collect the existing `StateFlow` and call the existing `AppLauncher`.

### Validation

- Feature Compose/UI tests for labels, empty state, and click events.
- App integration test proving taps retain the correct `AppIdentity`.
- Device observation of labels and launch.
- On a multi-profile device, distinct profile identities remain distinguishable.

### Exit evidence

A user can see installed launchable activities and launch a selected activity without restarting ProtoLaunch.

## A3 — Live package mutation proof — complete

**Owners:**

- Callback/platform behavior: `:platform:launcherapps`
- Visible-state observation: `:app` and `:feature:home`

Use the isolated `:fixtures:launchable` installable launcher fixture for a safe package-add/remove procedure. It is test infrastructure and has no production dependency. The production manifest has a narrow `MAIN`/`LAUNCHER` `<queries>` declaration for launchable activity visibility.

Before A3 device setup, run these exact preparation commands:

```text
env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :fixtures:launchable:assembleDebug
adb push fixtures/launchable/build/outputs/apk/debug/launchable-debug.apk /data/local/tmp/protolauch-a3-fixture.apk
```

Device evidence on SM-G986W (API 33): real `pm install` made the fixture appear and real `pm uninstall` removed it while the same ProtoLaunch Activity instance remained alive. Neither the Activity nor ProtoLaunch was restarted; this verifies framework-delivered package changes, not direct callback invocation. To reproduce, keep ProtoLaunch alive, run `adb shell pm install -r /data/local/tmp/protolauch-a3-fixture.apk`, verify the fixture is visible, then run `adb shell pm uninstall <fixture.applicationId>` and verify removal. Substitute the fixture's actual application ID.

### Verified device procedure

While the Activity remains alive:

1. Record the visible catalogue.
2. Install or enable a controlled launchable fixture.
3. Observe the new entry without Activity recreation.
4. Remove or disable it.
5. Observe its removal without restart.

### Exit evidence — complete

Package additions and removals update the visible inventory while ProtoLaunch remains alive.

Direct callback invocation tests do not satisfy this gate; real framework delivery is required.

## A4 — Correct-profile device proof

**Owners:**

- Launch platform: `:platform:launcherapps`
- End-to-end observation: `:app`

### Required proof

- Personal-profile launch.
- Work-profile launch where an accessible work profile exists.
- Same component identity distinguished between profiles.
- Launch uses the selected `ProfileId`, never the caller’s current profile as a fallback.
- Stale/inaccessible profile tokens fail safely.

### Exit gate

A selected profile-aware identity launches in the correct profile.

**Status: complete.** On PLG110 (API 36), profile 0 and managed work profile 11 exposed the shared `com.android.contacts/.DialtactsActivityAlias` as distinct profile-aware identities. Launch of the selected work identity was observed under `u11`. An inaccessible profile token returned false and produced no new personal ActivityRecord. Direct proof is split across `WorkProfileLaunchProofTest.kt` and `WorkProfileDeviceEvidence.kt`; function audit was clean. The A4 connected test explicitly skips on Samsung API 33 when no work profile exists.

## A5 — User-approved settings deferral

Settings persistence is explicitly deferred with user approval; it is not a Phase A implementation task or exit requirement. Do not implement Phase A settings. Introduce settings only with the first real setting-backed behavior and its owning data contract.

**Owners:**

- Domain/contract: appropriate core owner
- Data implementation: future `:data:preferences`
- Presentation: separate settings feature or bounded settings surface

Phase A/0B does not define or persist basic launcher settings. Android owns current HOME role/default-launcher selection; ProtoLaunch must request or observe that platform-owned role rather than persist a duplicate preference. Speculative Home 2, feed, and layout preferences are deferred until their corresponding behavior is implemented. Introduce settings persistence with the first real setting-backed behavior and its owning data contract; do not invent a setting for this phase.


## Deferred — Settings persistence

**Owner:** dedicated data owner, with thin app wiring

### Requirements

- Explicit defaults.
- Round-trip persistence.
- No preference writes from Activity or composables.
- Graceful reset behavior.
- Stable behavior across process death and restart.

### Exit gate

Deferred settings must persist through their owning data contract once a real setting-backed behavior is introduced; this is not a Phase A/0B gate.

## A5c — Full 0B device gate

**Status: complete.** The full Phase A evidence and required static validation are recorded at the top of this roadmap and in [`state-logs/handoffs/2026-09-24-phase-a-complete.md`](../state-logs/handoffs/2026-09-24-phase-a-complete.md).

Run the complete launcher-platform acceptance pass:

- HOME selection.
- Visible inventory.
- Tap launch.
- Personal-profile launch.
- Work-profile launch if available.
- Package addition/removal without restart.
- Process recreation.
- TalkBack smoke test.
- Full static validation.

### Exit gate for Phase A — complete

The required full device pass and static validation passed. Phase B (B1–B3) is complete; see the Phase B records below. This does not mark any later phase complete.

---

# Phase B — 1A grid engine

**Outcome:** A framework-free, tested placement engine that defines legal Home geometry without rendering UI.

**Owner:** `:core:grid`

## B1 — Coordinate, span, and collision model

**Status: complete as a phase-limited gate; generic-span generalization is open.** The durable B1 contract and evidence are recorded in [`docs/agents/tasks/b1-grid-coordinates.md`](agents/tasks/b1-grid-coordinates.md) and [`state-logs/handoffs/2026-09-24-b1-grid-complete.md`](../state-logs/handoffs/2026-09-24-b1-grid-complete.md).

Implement:

- `GridPosition`
- `GridSpan`
- Supported 1×1, 2×1, 1×2, 2×2, and 4×2 spans.
- Bounds checks.
- Collision checks.
- Move legality.
- Resize legality.

`GridSpan` already represents generic positive integers, but `GridRules` currently rejects anything outside the B1 set with `UNSUPPORTED_SPAN`. Per `docs/grid.md`, legality must generalize to bounds + rectangle collision for the open span set (3×2, 4×3, 3×3 as valid examples) before F3 claims tiles beyond the B1 set; renderable-size policy then moves to UI/editing slices. Persistence already stores generic dimensions and needs no migration for this change.

### Tests

- Edges.
- Invalid spans.
- Overlap.
- Holes.
- Same-item move.
- Replacement versus rejection.
- No mutation of unrelated entries.

## B2 — Vertical extent and stable ordering

**Status: complete.** The durable contract and evidence are recorded in [`docs/agents/tasks/b2-vertical-extent.md`](agents/tasks/b2-vertical-extent.md) and [`state-logs/handoffs/2026-09-24-b2-grid-vertical-extent.md`](../state-logs/handoffs/2026-09-24-b2-grid-vertical-extent.md).

`GridVerticalExtent` and `GridVerticalLayout.measure` provide pure measurement. The result is the exclusive occupied bottom plus working margin, bounded below by viewport rows. Arithmetic is `Long`-safe, has no arbitrary maximum, and preserves input placements and ordering.

### Tests

- Distant occupied row.
- Large sparse gaps.
- No arbitrary maximum row.
- Deterministic bottom boundary.
- Stable result across repeated calculation.
- No unrelated item movement.

## B3 — Column-count adaptation

**Status: amendment in progress.** The existing durable contract and evidence are recorded in [`docs/agents/tasks/b3-column-adaptation.md`](agents/tasks/b3-column-adaptation.md) and [`state-logs/handoffs/2026-09-24-b3-grid-complete.md`](../state-logs/handoffs/2026-09-24-b3-grid-complete.md); this correction is not complete until the source, tests, and validation pass.

`GridColumns` and `GridViewportAdaptation.columnsFor` expose pure column-count adaptation using the documented viewport/inset/padding formula. The API does not rewrite placements.

Six square cells across is the normal-phone reference/sweet spot, not a minimum. Narrow or split viewports may use fewer columns, and wider viewports may use more.

### Tests

- Phone width.
- Wide width.
- Foldable width.
- Insets.
- Stable back-and-forth adaptation.
- Adaptation does not rewrite logical source placement.

## Phase B exit gate

**Status: complete.** B1 legality/coordinates, B2 vertical extent, and B3 column adaptation pass their contracts and tests. Final validation evidence is recorded in [`state-logs/handoffs/2026-09-24-b3-grid-complete.md`](../state-logs/handoffs/2026-09-24-b3-grid-complete.md).

`:core:grid` can answer:

- Is placement legal?
- Where does it render?
- How far does the canvas extend?
- How does the logical layout adapt to a new width?

with no Android, Compose, database, or tile-kind dependencies.

---

# Phase C — 1B custom design primitives

**Outcome:** Launcher-owned Compose components required by the first real Home.

**Owner:** `:core:designsystem`

Do not implement every component named in the report at once.

## C1 — Geometry and layout primitives

- Implement reusable custom Compose geometry/layout primitives and small contracts in `:core:designsystem`, separate from screen-specific arrangement owned by `:feature:*`.
- Provide inputs/contracts for measured geometry, explicit anchors or regions, and insets so screen arrangements can position controls and overlays at bottom, top, side or floating locations without fixed-coordinate or bottom-navigation assumptions.
- Preserve the six-small-tile-wide Home layout as the normal-phone baseline/test arrangement, not a hard-coded screen contract. Support alternate screen shapes and grid widths through layout inputs while retaining stable logical content.
- Geometry may describe tile bounds, span containers, floating overlay frames and safe insets/control regions, but must not define a second placement/packing system. `:core:grid` remains the sole owner of placement legality, extent and projection; B3 remains pure count adaptation.
- This slice implements geometry/layout primitives only. It does not implement navigation destinations or prescribe a fixed navigation/control placement.

## C2 — App tile primitive

Small contract for:

- Label.
- Optional icon slot (pre-resolved symbol snapshot only; source resolution, normalization, and caching stay in `:platform:launcherapps`).
- Span.
- Symbol placement rule: bottom-right except 1×2 bottom-center, expressed generically.
- Separate hit targets from the start: `TileBody` / `AppLaunchButton` / `ShortcutSlot` (`Body` and `AppSymbol` both launch today so the body can later open the notification surface without geometry rework).
- Shortcut slots render only configured shortcuts (default empty, no placeholder controls).
- Semantic `TileColorRole.Normal/Accent` resolved to dynamic colors; never persisted as RGB.
- Selected/pressed state.
- Accessibility semantics.

No app inventory or database access.

## C3 — Floating control and overlay primitive

- Measured interactive boundary.
- Hit-test priority.
- Independent overlay semantics.
- Focus trapping behavior where appropriate.

## C4 — Navigation control primitive

Only after actual destinations exist. Initial implementation can provide the reusable shape/geometry without pretending all destinations work.

### Validation

- Compose UI tests.
- Screenshot or device visual checks.
- Font scaling.
- Touch-target sizes.
- Focus order.
- TalkBack labels.
- Reduced-motion behavior.

### Phase exit gate

The next Home slice can compose primitives without embedding business logic in a screen composable.

---

# Phase D — 1C persisted Home

**Outcome:** One usable Home whose tiles survive restart and can be edited without auto-compaction.

## D1 — Logical placement schema

**Owners:**

- Domain model: `:core:model` or grid-facing model owner
- Persistence: future `:data:database`
- Repository contract: future data repository

Persist:

- `homeId`.
- Stable item identity.
- Item type.
- Logical column and row.
- Span (generic positive integers; the B1 allowlist is legality policy, not the stored model).
- Logical tile configuration (future `TileAppearance` color role + `ConfiguredShortcut` slots via explicit migration when their slice lands; D1 adds no speculative fields).

No framework objects or pixels.

### Tests

- Schema migration.
- Round trips.
- Transactions.
- Invalid references.
- Profile-aware identities.
- Same component in separate profiles remains separate.
- Missing installed app becomes stale/unavailable rather than silently changing identity.

## D2 — Repository operations

Implement:

- Load Home.
- Place item.
- Move item.
- Resize item.
- Remove item.
- Replace transaction safely.
- Future `homeId` support without migrating the same item between Homes.

Repository delegates all legality to `:core:grid` and preserves unrelated rows/order/holes on every op. When grid legality generalizes per the B1 note above, D2 needs no contract change: it keeps passing through the grid result unchanged.

## D3 — Viewport-aware Home rendering

**Owners:**

- `:feature:home`
- `:core:grid`
- `:core:designsystem`
- Thin `:app` wiring

Requirements:

- Six square columns are a baseline example for an average phone, not a fixed minimum; the renderer must use the adapted column count and must not re-clamp it to six.
- One vertically scrolling canvas.
- Floating chrome above the scrolling grid.
- Only visible and near-visible items composed.
- Sparse distant rows do not allocate every intervening row.
- App labels/icons resolve outside recomposition.
- Dedicated absolute-coordinate layout from logical cells (never `LazyVerticalGrid`, never `Column { Row { } }`); shared gap/padding/symbol tokens so a multi-cell tile is exactly its cells plus internal gaps.

D3 is read-only presentation: it does not yet claim the `docs/grid.md` tile content contracts (symbol bottom-right/1×2 bottom-center rule, `Body`/`AppSymbol`/`Shortcut` hit separation, `TileColorRole` theming, configured shortcut slots). Those land with the tile primitive and editing slices.

## D4 — Basic move editing

Add long-press and move.

### Tests

- Valid target.
- Invalid collision target.
- Cancellation.
- Preserve unrelated items.
- Preserve holes.
- Restart after move.

## D5 — Resize editing

Support the required spans incrementally, beginning with the spans the UI actually renders.

### Tests

- Supported resize transitions.
- Rejected collision.
- Span legality.
- Restart.

## D6 — Remove and reset behavior

- Remove from Home.
- Reset Home.
- Handle removed apps distinctly from user removal.
- No destructive migration of catalogue data.

### Phase exit gate

A phone user sees six-column Home tiles, launches them, moves/resizes/removes them, and sees the same logical Home after restart without unrelated tiles shifting.

---

# Phase E — 1D folders

**Outcome:** Persistent folders open as floating, independently scrollable overlays over the current Home.

## E1 — Folder persistence

**Owner:** dedicated data repository

Persist:

- Folder identity.
- Home ownership.
- Membership.
- Logical positions.
- Future tile configuration.

Reject or define:

- Orphans.
- Cross-Home membership.
- Duplicate membership.
- Invalid positions.
- Concurrent membership conflicts.

## E2 — Folder open/close presentation

**Owners:**

- `:feature:folders` or bounded Home feature
- Shared design primitives

Folder is not a navigation destination.

### Required behavior

- Opens over the current Home.
- Home remains underneath.
- Folder is independently scrollable.
- Six square cells across on a normal phone.
- Back and outside-tap dismissal follow the defined overlay policy.
- Accessibility focus remains coherent.

## E3 — Folder launch and edit events

- Tap launches the selected profile-aware identity.
- Reorder uses grid legality.
- Invalid move is rejected without compaction.

## E4 — Transfer in/out and remove folder

- Add app to folder.
- Remove app from folder.
- Move app to Home.
- Remove empty/nonempty folder using an explicit policy.
- Unrelated Home and folder entries remain unchanged.

### Phase exit gate

Folders persist, open as overlays, launch correctly, edit safely, and do not alter the underlying Home unexpectedly.

---

# Phase F — Extend the Home foundation

## F1 — Independent Home 2

Prerequisites: stable `homeId` and completed folders.

- Enable/disable setting.
- Home selection state.
- Separate placements and scroll state.
- Same app can have different positions/configurations per Home.
- No data migration between Homes during normal use.

## F2 — Shortcut gateway

**Owner:** future `:platform:shortcuts`

- Query authorized launcher shortcuts.
- Preserve profile identity.
- Expose stable framework-free descriptors.
- Revalidate at launch time.
- Handle revoked, removed, and unavailable shortcuts.

## F3 — Expanded custom app tiles

Prerequisites: resizing, shortcut/content contracts, tile configuration persistence, and the B1 generic-span generalization above.

Implement incrementally:

1. 2×1.
2. 1×2.
3. 2×2.
4. 4×2.
5. Grid.md open-set examples: 3×2, 3×3, 4×3 (generic dimensions, not a second enum).

These are launcher-owned surfaces, not Android widgets. Each step keeps `Body`/`AppSymbol`/`Shortcut` hit separation and the generic symbol-placement rule intact.

## F4 — Filtered packing projection

**Owner:** `:core:grid`

Create a separate deterministic API for generated projections.

### Required rules

- Pack toward the bottom.
- Avoid the measured floating-control floor.
- Support mixed spans.
- Deduplicate by profile-aware identity.
- Preserve original Homes unchanged.
- Be stable across input order changes.

## F5 — Show All Apps projection

- Uses installed-app inventory.
- Does not mutate source Home placements.
- Resolves duplicates deterministically.
- Accounts for tiles appearing on both Homes.

## F6 — Show All Widgets projection

Only after real widget hosting exists.

## F7 — Four-destination navigation shell

Add destinations only as their owners become real.

A shell button must not claim to be a working All Apps, Messages, or News surface.

### Phase exit gate

The launcher supports two independent vertical Homes, expanded launcher-owned tiles, and honest filtered projections without changing normal free-placement semantics.

---

# Phase G — All Apps and locally sourced recents

## G1 — Grouped All Apps

**Owner:** future `:feature:allapps`

Primary presentation:

- Categories.
- Category tiles.
- Profile-aware identity.
- Deterministic classification outside composition.

Work-profile apps are not mixed among ordinary personal apps and are not distinguished with badges. The grouped App Library has a permanent **Work Apps** group/folder; assign entries to it by profile identity, retain each full profile-aware `AppIdentity` (including `ProfileId`, for example profile 11), and preserve that identity through launch. If the work profile is inaccessible, show an explicit unavailable state; if it is accessible but has no launchable apps, show an explicit empty state. This is a Phase G presentation contract, not a change to the Phase A catalogue.

Manual category overrides require a separate data owner.

## G2 — Alphabetical/list mode

The same inventory can switch to a list representation through a downward gesture or control. It follows the `docs/grid.md` app-list contract, which the current flat All Apps list does not yet meet:

- Uniform row geometry: symbol left, name beside it, configured shortcuts right; no app-derived row colors, icon backgrounds, or card styling.
- One shared symbol pipeline with Home tiles (monochrome > foreground > legacy + normalization + cache).
- Sibling hit targets: launch region vs each shortcut region are separate, never nested clickables.
- Shortcut regions appear only for configured shortcuts.

Plus:

- Accessible scrolling.
- Stable locale-aware sorting.
- Correct profile labels.
- Stale-item handling.
- Return to grouped mode without losing navigation position unnecessarily.

## G3 — Launcher-owned recent launches

**Owners:**

- Platform launch reporting
- Dedicated data owner
- Home/All Apps presentation

Record only launches initiated by ProtoLaunch:

- Stable identity.
- Timestamp.
- Optional bounded count.
- Retention limit.

Do not require Usage Access.

## G4 — Frequent app circles

- Four intended targets on normal phone geometry.
- Deterministic ranking.
- Separate profiles.
- Missing-app handling.
- Restart persistence.

## G5 — Recent Files

Start with a SAF picker and user-authorized documents.

Do not claim complete device-wide recent-file history without explicit user grants and indexing.

## G6 — Permanent group policy

All Apps should include:

- Recent Files.
- Frequent Apps.
- Recent Texts/Messages when available.
- Work Apps, as a permanent profile-separated group/folder.
- Other legitimate source groups.

Unavailable or empty groups need explicit, truthful states. Work Apps must distinguish an inaccessible/unavailable profile from an accessible profile with no launchable apps; do not substitute profile badges or mix its entries into personal groups.

## G7 — Shortcut actions in All Apps

Only after the shortcut gateway is stable.

### Phase exit gate

All Apps is a real grouped inventory with an accessible list mode and honest locally sourced recent groups, without depending on Message Center or widgets.

---

# Phase H — Notifications and Message Center

## H1 — Notification access onboarding

**Owner:** future `:platform:notifications`

- Explain launcher-captured history.
- Open notification listener settings.
- Observe enabled/disabled state.
- Do not claim access before confirmation.

## H2 — `NotificationListenerService`

**Owner:** `:platform:notifications`

- Normalize posted notifications.
- Normalize ranking changes.
- Normalize removals.
- Resolve package/app metadata at the platform boundary.
- Work off the UI thread.
- Deduplicate by stable source/event identity.

## H3 — Notification-history repository

**Owner:** dedicated data owner

Persist bounded normalized records:

- Source/profile identity.
- App.
- Package or channel.
- Conversation/sender identity when available.
- Timestamp/rank.
- Safe display fields.
- Retention policy.
- Revocation behavior.

Never store live `Notification`, `PendingIntent`, or framework objects.

## H4 — Message classification

**Owner:** domain/data owner

Priority order:

1. Explicit user rules.
2. Conversation/sender/channel.
3. Transparent app/category heuristics.
4. Other.

“Other” is not deletion.

## H5 — Message Center presentation

**Owner:** future `:feature:messages`

Views:

- Priority.
- Conversations.
- Mail.
- Dynamic categories.
- Other.

Each card remains understandable without color alone.

## H6 — Recent actions

Use stable descriptors, not live intents:

- Notification actions.
- Shortcut actions.
- Local launcher actions.
- Later contacts/call/email actions if separately authorized.

Resolve at click time and fall back to the source app when an action expires.

## H7 — Access revocation

Prove:

- Listener disabled.
- Service disconnected.
- UI returns to an honest unavailable state.
- Existing retained policy is explicit.
- No continued background parsing.

### Phase exit gate

Message Center displays only launcher-captured notification history after explicit access, with deterministic classification, bounded persistence, and honest revocation behavior.

---

# Phase I — Global search

## I1 — Search contract

**Owner:** future `:feature:search`

Search is an orchestrator over independent providers, not a global platform repository.

Each provider exposes:

- Stable result identity.
- Display fields.
- Availability/permission state.
- Cancellable query.
- Framework-free launch descriptor.

## I2 — App search provider

- Installed apps.
- Profile-aware identity.
- Locale-aware matching.
- No database work in recomposition.

## I3 — Shortcut and action providers

- Authorized shortcuts.
- Launcher recent actions.
- Expired-action fallback.

## I4 — Message-history search

Only after Message Center persistence exists.

## I5 — Contacts

Separate runtime permission and provider.

- No pre-grant.
- Revocation state.
- Local matching off the UI thread.

## I6 — Files

Only user-authorized SAF documents.

## I7 — Ranking, cancellation, and UX

- Debounced input.
- Cancellable stale query cancellation.
- Deterministic ranking.
- Source grouping.
- Profile-safe action.
- Keyboard and TalkBack behavior.

### Phase exit gate

Search works across independently owned sources, reports unavailable providers honestly, and never blocks frames or bypasses permissions.

---

# Phase J — Real Android widgets

This phase is late because widget hosting requires stable placement and editing.

## J1 — Widget provider discovery

**Owner:** future `:platform:widgets`

- `AppWidgetManager`.
- Provider metadata.
- Availability and compatibility policy.

## J2 — Widget ID allocation and persistence

**Owner:** widget platform + data owner

- Allocate stable IDs.
- Persist provider ID and widget ID.
- Handle process death.
- Clean up removed widgets.

## J3 — Bind and configure

- User-driven bind flow.
- `AppWidgetHostView` creation.
- Configuration persistence.
- Denial and cancellation.

## J4 — Host in Compose

- AndroidView boundary.
- Correct sizing by span.
- Save/restore.
- Resize handling.
- Provider removal.

## J5 — Widget editing

- Move.
- Resize.
- Remove.
- Cancel.
- Restart.

## J6 — Replace expanded tile with compatible widget

Only after both expanded app tiles and real widgets exist.

This is a transactional launcher edit.

## J7 — Show All Widgets projection

Feed real widget records into the filtered packing engine.

### Phase exit gate

A real third-party widget is discovered, configured, hosted, resized, restored, and removed correctly.

---

# Phase K — News Feed

## K1 — Provider feasibility spike

Before production implementation, verify for each of Lawnfeed and Neo Feed:

- Discoverability.
- Service/binder protocol.
- Authentication.
- Security requirements.
- Version compatibility.
- Redistribution constraints.
- Failure/reconnect behavior.

This is a blocking investigation, not permission to invent an API.

## K2 — Provider-neutral platform contract

**Owner:** future `:platform:feed`

States:

- Disabled.
- Provider unavailable.
- Connecting.
- Connected.
- Recovering.
- Permission denied.
- Protocol incompatible.

## K3 — Feed preference

Owned by data/settings, not the Compose screen.

## K4 — Feed presentation

**Owner:** future `:feature:news`

- Provider content only.
- No fabricated fallback news backend.
- Connection/retry state.
- Source/profile semantics.
- External article actions.

## K5 — Per-provider device proof

Prove each provider independently. One provider working does not satisfy a two-provider requirement.

If interoperability cannot be established, document the unmet acceptance condition and request a product decision.

### Phase exit gate

Authorized providers can be connected, consumed, disconnected, and recovered without misleading users or embedding provider-specific knowledge in Home.

---

# Phase L — Global navigation and gesture arbitration

## L1 — Navigation state owner

Create a dedicated navigation/presentation owner when code requires it.

Keep separate:

- Selected destination.
- Home selection.
- Active folder.
- Drag/resize mode.
- Feed reveal progress.

Do not combine these into one ambiguous enum.

## L2 — Floating navigation pill

- Centered compact pill.
- Four circular destinations.
- Concentric dimensions from the report.
- Individually drawn chips.
- System insets.
- Accessible names and focus order.

## L3 — Gesture priority policy

Define and test priority:

1. Active drag/resize.
2. Widget-owned touch.
3. Folder overlay interaction.
4. Vertical Home scroll.
5. Feed reveal.
6. Tap.

## L4 — Directional lock and touch slop

- Diagonal gesture tests.
- Nested scroll behavior.
- System-edge exclusion.
- Interrupted animations.
- Back-button priority.

## L5 — Feed-following transition

- Reveal tracks the finger.
- Threshold decides completion.
- Cancellation settles predictably.
- Reduced-motion alternative.
- No animation state written to persistence.

### Phase exit gate

All available surfaces can be reached predictably without accidental gesture conflicts, and unavailable destinations are not presented as functional.

---

# Phase M — Foldables, large screens, and adaptation

## M1 — Wide-column grid

Prerequisites: pure B3 adaptation.

- Fewer or more than six columns where appropriate.
- Stable logical-to-visual projection.
- No source-coordinate rewrite.
- Back-and-forth restoration.

## M2 — Hinge and insets

- Hinge-aware available regions.
- System bars.
- Floating-control safe area.
- Split-screen resize.

## M3 — Folder adaptation

Clarify and implement:

- Six-column folder behavior on wide viewports.
- Split-pane behavior.
- Overlay versus pane behavior.

## M4 — Device proof

Use at least:

- One normal phone.
- One representative foldable/tablet/emulator configuration.

### Phase exit gate

The same persisted logical Home adapts predictably without destructive reflow.

---

# Phase N — Accessibility, performance, privacy, and final acceptance

These are not one final paint pass. Each owning slice must satisfy relevant gates during implementation, followed by a cross-product audit here.

## N1 — Accessibility

Per surface:

- TalkBack labels and roles.
- Spoken profile disambiguation.
- Work Apps group/folder and its empty/unavailable status are announced accessibly; work-profile membership is conveyed by the separate group, not badge-only treatment.
- Focus order through overlays.
- Keyboard alternatives for drag, resize, and swipe.
- Touch-target size.
- Contrast.
- Font scaling.
- Reduced motion.
- Visible focus.
- Screen-reader descriptions of unavailable sources.

## N2 — Performance

Create a benchmark module only when benchmark code exists.

Measure:

- Cold HOME startup.
- Warm Activity recreation.
- Catalogue refresh.
- Package mutation response.
- Sparse vertical scrolling.
- Distant occupied rows.
- Folder opening.
- Search latency.
- Notification ingestion.
- Widget loading.

Do not perform during recomposition:

- Catalogue enumeration.
- Icon decoding.
- Classification.
- Notification parsing.
- Database work.
- Search.

## N3 — Privacy and retention

- Bounded notification history.
- Explicit SAF grants.
- Permission revocation.
- No sensitive logs.
- No raw device dumps.
- Backup policy.
- Retention reset.
- No hidden contact/message indexing.

## N4 — Whole-launcher device matrix

At minimum:

- HOME selection.
- App display.
- Personal-profile launch.
- Accessible work-profile launch.
- Package add/remove.
- Process death and restart.
- Two independent Homes if enabled.
- Editing.
- Folders.
- All Apps.
- Message Center after access.
- Search.
- Real widget provider.
- Feed provider or explicit blocked acceptance.
- Rotation.
- Wide/foldable viewport.
- TalkBack.
- Large font.
- Reduced motion.

## N5 — Final static validation

Run exactly:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Also run relevant module and instrumentation tests.

---

# 4. Immediate next slices

Completed and device-verified: Phase A / 0B, Phase B (B1–B3 legality/extent/adaptation), Phase C tile/geometry/overlay primitives (initial), Phase D D1–D3 (schema, repository, viewport-aware rendering + app migration), and the navigation shell + flat All Apps + add-to-home slice (`885082b`). The flat All Apps list is a stepping stone: it does not yet meet the `docs/grid.md` list contract (uniform rows, sibling hit targets, shared symbols, configured-shortcuts-only).

Candidate next sequence (confirm with user before starting):

1. **Grid legality generalization** — bounds + collision for the open span set (B1 follow-up; unblocks F3 beyond 4×2).
2. **D4 — Move editing** (long-press, valid/invalid/cancel, holes preserved, restart).
3. **D5 — Resize editing** (supported transitions first, then open-set sizes).
4. **D6 — Remove/reset** (user removal vs missing-app unavailability).
5. **Tile content slice** — `Body`/`AppSymbol`/`Shortcut` hit separation, generic symbol placement, `TileColorRole` theming, configured shortcut slots, shared symbol pipeline hardening.
6. **App-list contract slice** — bring the flat list into `docs/grid.md` compliance.
7. **E1 — Folder persistence**
8. **E2 — Floating folder presentation**

A5 settings persistence remains a user-approved deferral and is not implementation work. Do not mark any later phase complete until its own gate is met.

---

# 5. Phase A completion record

## A2 — Completed: `HomeCatalogueState` catalogue presentation

**Primary owner:** `:feature:home`  
**Thin integration owner:** `:app`

### Inputs

- `HomeCatalogueState` (`Loading` / `Content`) in `:feature:home`, wired from the platform catalogue by `:app`
- Existing `AppLauncher`
- Existing `LauncherAppEntry.identity`

### Output

A custom, accessible list showing labels and dispatching the selected profile-aware identity for launch.

### Constraints

Do not:

- Add icons yet.
- Change the platform catalogue contract.
- Introduce grid placement.
- Add persistence.
- Move inventory into the Activity.
- Put Android framework types in the feature.
- Build All Apps or search.

### Acceptance criteria — satisfied

- Labels are visible.
- Empty and populated states are defined.
- Each item’s click retains the exact `AppIdentity`.
- The Activity uses the existing `AppLauncher`.
- Unit/Compose tests prove state and events.
- App instrumentation proves click-to-launch wiring.
- Device observation shows installed launchable activities.
- Multi-profile identities remain distinct where testable.
- Prescribed Gradle and repository audits pass.

---

# 6. Risks and decisions to resolve before relevant slices

## Before persistence

Decide:

- “Page” versus independently keyed vertical Home terminology.
- Whether tile configuration is per Home.
- How folder membership is scoped.
- How missing installed apps are represented.
- How identity behaves after profile removal/recreation.

## Before filtered projections

Decide:

- Which tile wins when one identity appears on both Homes with different spans/configurations.
- Whether widgets are placement-distinct.
- How control-floor measurement changes packing.
- How stable ordering behaves across locale and width changes.

## Before notification storage

Decide:

- Retention duration.
- Display-field redaction.
- Conversation identity fallback.
- Content-pattern rule scope.
- Behavior after notification access is revoked.

## Before documents and contacts

Decide:

- Persisted SAF grant lifecycle.
- Whether indexed recents are required for acceptance.
- Contact indexing and revocation behavior.

## Before feed implementation

Require a real provider feasibility result. Do not infer compatibility from launcher support in other products.

## Before performance closure

Set measurable budgets for:

- HOME startup.
- Catalogue refresh.
- Scroll frame time.
- Search latency.
- Notification processing.
- Widget load.

---

# 7. Explicit deferred non-goals

Do not pull these forward:

- AI-only app classification.
- Complete SMS access.
- Complete historical Android notification access.
- Usage Access as a prerequisite.
- Global file indexing without explicit grants.
- A fabricated News Feed backend.
- Assuming Lawnfeed/Neo Feed interoperability.
- A second packing system in a feature.
- Real widgets before stable placement/editing.
- Four polished navigation buttons wrapping nonworking destinations.
- Stock Material navigation, cards, FABs, search bars, or chips as substitutes for the specified custom launcher components.
- Horizontally paged Homes.
- Auto-compacting ordinary Home placement.
- Persisting framework objects or pixels.

---

# 8. Whole-launcher definition of done

ProtoLaunch is complete only when:

- It is selectable and stable as HOME.
- It visibly lists launchable activities.
- App identity includes component and profile.
- A selected entry launches in the correct profile.
- Installs/removals update the visible list without restart.
- Basic settings persist.
- Normal phone Home uses six square cells across.
- Required spans work.
- Home scrolls vertically.
- Normal placement preserves holes and unrelated tiles.
- Home state survives restart.
- Move, resize, and remove are safe.
- Folders persist and open as floating overlays.
- Two independent vertical Homes work if enabled.
- Filtered projections pack deterministically without changing source Homes.
- All Apps provides grouped and list presentations.
- Recent/frequent sources are honest about authorization and availability.
- Message Center uses only launcher-captured notification history.
- Search aggregates independent providers safely.
- Real widgets are hosted separately from expanded tiles.
- Feed providers are genuinely proven or their unmet requirement is explicitly reported.
- Navigation and gestures do not conflict.
- Foldable/wide adaptation is verified.
- Accessibility, privacy, and performance gates pass.
- Final static and device validation are recorded.

No shell, placeholder, green build, or plan alone satisfies the final gate.
