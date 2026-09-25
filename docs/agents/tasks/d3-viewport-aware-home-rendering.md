# D3 — Viewport-aware rendering of one persisted Home

Status: in progress; feature/UI prerequisites and app migration are implemented, while production-device exit evidence remains.  
Phase: D / 1C persisted Home  
Owners: `:feature:home`, `:core:grid`, `:core:designsystem`, thin `:app` wiring, `:data:repository` read contract, and `:platform:launcherapps` catalogue/icon resolution

## Objective and bounded outcome

Render the persisted placements of exactly one explicitly selected `HomeId` in a single vertically scrolling, viewport-adapted Home canvas. This is a read-only presentation slice: it loads persisted placement state, resolves current catalogue metadata, computes a grid viewport/extent/window, renders available and unavailable placements, and dispatches launch events for taps on available apps.

The selected `HomeId` is an explicit input to the loader/presentation boundary. D3 does not add Home selection or create a default Home. It does not edit or provision persisted data. Repository reads remain the existing D2 explicit `load(homeId, bounds)` operation; D2's operation contract remains load/place/move/resize/remove and gains no implicit Home creation, selection, observation, or `Flow`.

`LoadResult.NotFound` and an existing Home with zero placements are distinct, honest states. Do not auto-create a Home, populate it from the app catalogue, or silently convert installed catalogue entries into persisted placements. A later explicit Home provisioning/population contract is a prerequisite to claiming the full Phase D gate: completing D3 alone does not make an empty/not-found Home populated or make Phase D complete.

### Replace the Phase A Home body

D3 replaces the Phase A catalogue list as the actual production Home body so the app begins taking shape as a persisted-placement launcher. The Home route renders the persisted-placement canvas and its honest loading, loaded-empty, or not-found state; it does not retain a catalogue list beside, below, or as a fallback for that canvas. Catalogue entries are metadata only for placements that already exist. Until an explicit placement/provisioning path exists, catalogue entries do not appear as Home tiles.

Keep the platform/backend catalogue contract intact: `AppCatalogue`, `LauncherAppEntry`, profile-aware `LauncherApps` enumeration, labels, and package/profile callbacks remain owned by `:platform:launcherapps` and available as the future All Apps source. Do not delete, relocate, or reimplement platform inventory work in `:feature:home`. Remove the old `HomeCatalogueList` / `HomeCatalogueState` presentation from the production Home route during D3, or explicitly migrate/mark those Home-only presentation types for deletion; deliberately migrate or update their focused tests to cover the replacement Home canvas and its states. Do not implement All Apps, add a fake placeholder destination, or preserve a duplicate catalogue list under Home.

## Explicit exclusions

Do not implement or imply:

- D4 move/edit gestures, D5 resize, or D6 remove/reset.
- Folders, widgets, shortcuts, expanded/custom span app content beyond current rendering, tile appearance roles, configured shortcut slots, `Body`/`AppSymbol`/`Shortcut` hit separation, generic symbol-placement rule, and dynamic-color theming — `docs/grid.md` tile content contracts deferred to the tile primitive and editing slices; D3 claims read-only presentation only.
- Other tile kinds beyond D1's `APP` placements.
- All Apps, search, global navigation, or fake News/Message Center/All Apps destinations.
- A catalogue-list fallback/duplicate in the production Home body; the platform catalogue itself remains intact for metadata and future All Apps use.
- Home creation, default-Home behavior, selection UI/state, placement provisioning, catalogue-to-placement synchronization, or D2 Flow/observation changes.
- Any mutation, compaction, repacking, or rewriting of stored logical coordinates when viewport width changes.
- A global manager, duplicate grid/packing/extent policy, or Android/Compose/database objects in core model or Room.

## Ownership and dependency direction

- **`:feature:home`** owns Home presentation and arrangement, immutable feature-facing render state, asynchronous loading orchestration, launch event dispatch, and transient per-Home scroll position. It requests geometry/windowing from grid and renders through design-system primitives; it does not duplicate placement legality, extent, viewport adaptation, catalogue state, or persistence.
- **`:core:grid`** owns logical geometry, `GridViewportAdaptation`, visible-row/window selection and overscan arithmetic, extent through `GridVerticalLayout.measure`, and any legality policy. D3 is read-only and should not add edit legality merely for drawing. New row-window APIs are pure, framework-free, overflow-safe and use logical row values.
- **`:core:designsystem`** owns reusable Compose sparse/windowed layout and generalized measured geometry for any positive visual column count, plus the reusable `AppTile` / `FloatingControlOverlay` primitives and a small boundary for drawing a pre-resolved icon snapshot. Six columns is the normal-phone reference/test arrangement, not a minimum. It does not choose Home arrangement or own scroll state, repository access, app lookup, or grid policy.
- **`:app`** performs thin construction and lifecycle wiring: it supplies the chosen `HomeId`, connects repository/catalogue/launch contracts to the feature, and forwards lifecycle-appropriate inputs. No catalogue, layout, persistence, or business logic accumulates in the Activity.
- **`:data:repository`** owns persisted reads. D3 consumes D2's explicit load result and does not change that contract or write data.
- **`:platform:launcherapps`** owns the profile-aware catalogue and icon resolution/cache, translating Android objects into safe feature inputs. Maintain cache invalidation with catalogue invalidation.

Keep dependencies inward: app wiring to feature/platform/data implementations; feature to core contracts; `:core:grid` to framework-free model; design system to its core geometry/model contracts. Do not introduce feature-to-feature implementation dependencies or make core depend on Room, Android services, or Compose.

## Feature-facing data and loading contract

Define immutable render inputs/state in the feature-facing boundary with these facts represented explicitly:

- The exact `HomeId` requested, retained in every loaded state so content cannot be mistaken for another Home.
- An ordered list of persisted D1 `AppPlacement` records for that Home, retaining each stable `ItemId`, full `AppIdentity` (profile plus complete component identity), logical position/span, and persisted availability. Preserve repository ordering; do not sort by label, deduplicate identities, or omit `UNAVAILABLE` items.
- Current app metadata keyed by the **full `AppIdentity`**, not component/package alone. Metadata contains the platform-resolved label and an optional pre-resolved icon snapshot suitable for rendering. Metadata lookup must not alter placement identity or order.
- A measured viewport description sufficient for B3 column adaptation and design-system geometry (available width/height, relevant insets/padding/gap and cell-sizing inputs), and the resulting adapted column count/measurements as appropriate. Keep pixels/Compose state out of persisted and core domain models.
- Explicit loading, loaded, and not-found states. A loaded state with no placements is distinct from loading and not-found. If data and catalogue resolution complete at different times, represent a coherent intermediate/loading state rather than presenting fabricated placements or blocking composition.

The feature loader invokes the repository off the UI thread and outside composition. It requests only its explicit `HomeId`; it does not create missing data. Avoid database/repository work from composables, and do not add a `Flow` to D2. Catalogue state may use the existing platform-owned observable inventory contract; app wiring coordinates updates without making feature state the catalogue source of truth. Retain stale placements if metadata is absent. Missing/unavailable metadata renders a label-only unavailable tile, preserves the full identity in render data, and does not dispatch a launch.

The catalogue's continued presence in platform/app wiring supplies metadata and preserves the future All Apps source; it is not an alternative Home content source. The production Home route must no longer show the Phase A `HomeCatalogueList` / `HomeCatalogueState` list. Keep or migrate the underlying platform contract and its inventory behavior, but remove/migrate the Home-only list presentation and deliberately update its focused tests rather than leaving a second catalogue presentation in Home.

## Viewport, geometry, scrolling, and sparse row window

- Use the count returned by the existing pure `GridViewportAdaptation.columnsFor` contract for width/count adaptation, without re-clamping it to six. Six square 1×1 cells across is the normal-phone reference/sweet spot, not a minimum or hard-coded lower bound. Narrow or split-screen viewports may truthfully adapt to fewer positive columns; wider viewports may adapt to more. Generalized design-system geometry must accept any positive visual column count, with six as its normal-phone reference/test arrangement. Adaptation changes only visual column count/cell geometry: logical persisted placement coordinates remain stable and must never be rewritten as pixels or new coordinates.
- Do not treat `GridColumns.MINIMUM_COUNT`, or any six-column clamp in the Home feature or design system, as a D3 requirement. Do not add a second count adaptation/clamping policy outside `:core:grid`.
- Use `GridVerticalLayout.measure` / `GridVerticalExtent` as the sole source of canvas row extent, including viewport floor and requested working margin. Do not calculate a competing feature/design-system extent or allocate one Compose child/row per logical row.
- Add a pure `:core:grid` row-window/overscan API that accepts a viewport's logical visible row range and overscan policy and returns a safe logical row range/window. Define and test behavior for empty viewports, invalid/reversed ranges, negative/zero overscan if permitted, and arithmetic near `Long` limits. Clamp or report overflow explicitly; never wrap. Keep row indexes/ranges wide enough to represent B2's unbounded-in-practice logical extent (do not narrow distant rows to `Int` prematurely).
- The `:core:designsystem` sparse/rebased layout consumes already arranged placements and a grid-owned visible/near-visible row window. It composes only intersecting visible and overscan placements. It must not enumerate/allocate the empty rows between distant placements. Rebase visual coordinates relative to a nearby logical origin using safe arithmetic so a placement at a distant legal row does not overflow Compose's `Int` pixel/position representation. It must not silently drop placements due to narrowing, overflow, unknown metadata, or being distant: off-window children are omitted from the current composition because they are outside the window, while the complete placement collection remains available and can render when scrolled into range. Handle spans crossing a window boundary deterministically.
- `:core:grid` remains sole owner of legality, logical extent, viewport adaptation, and row-window selection. Design-system geometry converts bounded measured geometry to pixels; feature arrangement does not become a second placement engine. Do not claim arbitrary infinite pixel scrolling: document/test the mapping and rebasing behavior for distant logical rows without overflow or loss.
- Keep vertical scroll state transient and feature-owned, keyed by the explicit Home identity if retained across composition. It is never stored in D1/Room. Preserve stable item identity and sensible scroll position through catalogue metadata refresh and viewport remeasurement.

## Catalogue metadata and icon handling

Labels are already platform-resolved through the app catalogue contract. Resolve optional icon snapshots outside recomposition and outside tile composition. Add a bounded cache at the platform/app icon boundary keyed by full `AppIdentity`; perform `LauncherApps`/`PackageManager` icon lookup and decode off the UI thread, and invalidate/update entries consistently with catalogue invalidation (including package/profile changes). Choose an explicit bounded eviction policy; do not turn this into a second catalogue.

No `LauncherApps`, `PackageManager`, `loadIcon`, icon decode, database/repository call, or blocking metadata lookup may occur in `:feature:home` composable bodies or tile composition. No `Drawable`, `UserHandle`, bitmap/framework object, Compose state, or pixels may enter `:core:model` or Room. If an icon is absent, render the label-only state; if the app is missing from current metadata, mark/present it unavailable without changing the persisted placement's identity or deleting it. A successful click passes the exact `AppIdentity` to the existing launcher boundary, preserving profile identity.

## Interaction and floating chrome

- Tapping an available `AppTile` dispatches its exact profile-aware `AppIdentity` to the existing launch contract. Unavailable entries remain visible and identifiable but are not launchable. There is no long-click, edit affordance, or gesture state in D3.
- Place `FloatingControlOverlay` above the scrolling canvas as a separate floating layer; it must not scroll with Home content or consume grid extent as an ordinary placement. Use only the truthful existing HOME-role request/control if it is still needed in this context. Do not present four destinations or controls implying unavailable features.
- Preserve the existing design-system semantics and accessibility behavior: meaningful labels/roles, at least 48dp interactive targets, coherent traversal/focus order between canvas and overlay, and no focus trap unless the overlay is modal by its actual behavior. Respect font scaling and reduced-motion behavior; do not make correctness depend on animation.

## Focused test plan

Add tests at the owning boundary, including at minimum:

1. **Feature loader/state:** explicit HomeId passed unchanged; loading → loaded populated; loaded-empty remains distinct; repository `NotFound` remains a not-found state and produces no provisioning/write; ordered placements including `UNAVAILABLE` and full profile-aware identity survive unchanged; no catalogue item becomes a placement. Exercise asynchronous completion/cancellation or stale request handling as needed so an old Home load cannot overwrite a newer explicit Home input.
2. **Home-route migration:** the production Home body is the persisted-placement canvas rather than `HomeCatalogueList` / `HomeCatalogueState`; focused tests formerly covering that presentation are migrated/updated for canvas loading, loaded-empty, not-found, populated content, and launch identity. Platform `AppCatalogue`, `LauncherAppEntry`, `LauncherApps` enumeration, labels, and callbacks remain intact and are not moved into the feature; no duplicate Home catalogue list or fake All Apps destination remains.
3. **Metadata/launch:** metadata is joined by full identity, including same component in different profiles; missing metadata produces label-only unavailable rendering without identity loss; available tap launches the exact identity; unavailable tap does not launch. Test metadata/catalogue update without reorder or placement mutation.
4. **Grid row window:** visible-row selection and overscan at top/bottom/empty windows, exact boundaries, spans crossing boundaries, distant sparse rows, negative/invalid inputs per contract, and arithmetic at/near `Long` limits. Prove deterministic output and no wrap/drop.
5. **Grid adaptation/extent integration:** normal-phone six-column square-cell reference arrangement; narrow and split-screen viewport cases in which the existing `GridViewportAdaptation` result is a positive count below six; alternate/wide viewport adaptation above six; inset/padding effects through existing B3; viewport floor/working margin through `GridVerticalLayout.measure`; and unchanged persisted source coordinates across all width changes. Assert D3 uses the returned count as-is, with no `GridColumns.MINIMUM_COUNT` or six-column clamp in feature/design-system code.
6. **Design-system sparse layout:** only visible/near-visible placements are composed; a large empty logical gap does not produce row/child allocation; a distant logical row can be scrolled/rendered through rebasing; placements do not disappear from the overall model due to `Int` pixel overflow; span geometry works for any positive visual column count (including fewer than six), with six as the normal-phone reference arrangement; icons are drawn from passed snapshots only.
7. **Compose/accessibility:** loading, not-found, loaded-empty, populated/unavailable tile states; click semantics; overlay is independent of scrolling and above the canvas; truthful HOME-role control only; target size >=48dp, labels/roles and traversal/focus remain usable. Assert no long-click/edit behavior is introduced.
8. **Ownership/purity:** inspect/test public feature/model contracts and Gradle graph to ensure no platform/Room/Compose framework object leaks into core model or Room, and no platform lookup, decoding, or database access happens in composition.

Use focused module unit and Compose/UI tests; add instrumentation only where the contract requires Android icon resolution or launch wiring. Tests of pure row-window arithmetic belong in `:core:grid`; reusable layout rendering tests belong in `:core:designsystem`; orchestration/state tests belong in `:feature:home`; `:app` coverage verifies thin wiring rather than duplicating module behavior.

## Device verification and required validation

After implementation, install the built APK on a connected test device with:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Record device/model/API, installation result, and observed behavior. On device, select ProtoLaunch as HOME if needed and verify that the production Home body is the persisted-placement canvas, not the Phase A catalogue list. Explicitly supply/use a provisioned test `HomeId`, verify populated and loaded-empty/not-found behavior honestly, observe six square columns on a normal phone, scroll sparse content to a distant placement, verify floating control remains above the canvas, and tap available entries (include a multi-profile identity where a work profile is available). Do not claim test population exists unless an explicit provisioning fixture/contract actually supplies it. Device installation alone is not runtime evidence.

Run focused tests first, then all required implementation checks (record exact results; distinguish `NO-SOURCE` from executed tests):

```text
./gradlew :core:grid:test
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:connectedDebugAndroidTest
./gradlew :feature:home:testDebugUnitTest
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Adapt focused task names only if the actual module build configuration requires it, and record the exact applicable task. Run the focused test suite relevant to changed modules rather than treating `NO-SOURCE` as a pass. Manual TalkBack speech, focus order, large-font/font-scaling behavior, reduced-motion behavior, and screenshots must be recorded as **unverified** if not actually run; automated semantics tests or successful builds do not substitute for those observations.

## Narrow exit gate

D3 is complete only when one explicitly supplied existing `HomeId` is rendered read-only from persisted D1/D2 placements; loading, not-found and loaded-empty are distinct and honest; available and unavailable placements retain order and full identity; labels and optional bounded/cached icon snapshots resolve outside composition; tapping an available tile launches the exact identity; the viewport adapts with existing grid contracts and stable logical coordinates; extent comes from `GridVerticalLayout.measure`; sparse, distant rows render through overflow-safe grid-owned windowing and rebased design-system layout without allocating intervening rows or silently losing placements; floating chrome is independent and truthful; focused tests, required validation, and device verification are recorded; and dependency/ownership constraints hold.

This gate does not imply D4–D6 or full Phase D completion. If there is no explicit Home provisioning/population contract and the only real result is not-found or loaded-empty, report that limitation. A later explicit provisioning/population slice is required before claiming the full Phase D gate described in the roadmap.
