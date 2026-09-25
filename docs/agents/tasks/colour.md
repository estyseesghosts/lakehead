Colour / Tile-surface task — Material theming, hit targets, symbols, grid tuning, immersive
1. Overview
This task brings the launcher's visual and interaction surfaces into line with docs/grid.md (§4, §5, §9, §10, §12), docs/design.md, and docs/immersive.md, without adding any new behaviour behind the new surfaces.
Motivation: today tiles are hardcoded dark (0xFF1E2126 on a light system theme over a white background), the whole tile is one combinedClickable, icons are unnormalized legacy snapshots rendered with no size or placement rule, the grid uses 16dp edge / 8dp gap, the reference width is six columns, and there is no immersive mode. The user wants Material dynamic colour, five-column reference width, 8dp-everywhere spacing, square tiles, generic symbol placement, separated Body / AppSymbol / Shortcut(i) hit regions, visual-only shortcut placeholders on home and in all-apps (left = add, right = remove), a hardened symbol pipeline, and transient-by-swipe immersive mode.
Each slice below is one commit. A slice lists its owning module, exact files, acceptance check, dependencies, and complexity so an implementing agent can execute it standalone.
Applicable user requirements (verbatim mapping is in each slice): (1) dynamic + fallback theming, (2) TileHitTarget.Body/AppSymbol/Shortcut(i), (3) square tiles + symbol positioning (bottom-right except 1×2 bottom-center), (4) home shortcut placeholders visual-only, (5) all-apps shortcut placeholders, (6) all-apps leftmost = add / rightmost = remove, (7) 5-column reference kept flexible, (8) 8dp edge = 8dp gap, (9) immersive transient-by-swipe edge-to-edge, (10) no home shortcut functionality, (11) grid.md + design.md conformance.
2. Current state summary
- Theme: no LauncherTheme, no MaterialTheme, no dark/light scheme. core/designsystem/AppTile.kt:52-63 hardcodes ContainerColor 0xFF1E2126, Selected 0xFF2C435E, Pressed 0xFF101216, SelectedBorder 0xFF8AB4F8, Content FFFFFFFF, TileShape RoundedCornerShape(12.dp). Manifest uses @android:style/Theme.Material.Light.NoActionBar. LauncherShell.kt is a plain Box(fillMaxSize) with no background composable.
- Grid: adaptive via GridViewportAdaptation.columnsFor() (not hardcoded). Production inputs in MainActivity.kt:70-84: horizontalPaddingPx=16dp, columnGapPx=rowGapPx=8dp, minimumCellPx=48dp. columnsBound=6 is a persistence bound. SquareCellGeometry.fromViewport derives cells from width − padding − gaps. No LauncherDimensions token type exists in source (docs-only).
- Tiles: AppTile.kt is one Box.background(...).combinedClickable(); HomeCanvasTile.kt maps render state onto it with a bare unconstrained Image(bitmap). No TileHitTarget, no TileColorRole, no ConfiguredShortcut, no symbol-placement rule.
- All-apps: AllAppsScreen.kt flat LazyColumn (Foundation BasicText only); AllAppsRow.kt is launch-Row(weight=1f).clickable + sibling Add/Added Box — sibling structure is already correct, but there are no shortcut slots and no remove control.
- Icons: LauncherAppsIconSnapshotRefresher does getBadgedIcon(0) → PNG → cache → decode. No monochrome/foreground extraction, no normalization, no tint.
- Immersive: none. No WindowCompat / WindowInsetsController usage (docs/immersive.md holds the exact pattern).
- Contracts that already agree: docs/architecture.md:24 and docs/design.md:7-10 specify TileColorRole.Normal/Accent → dynamic :31+ / static fallback, TileHitTarget.Body/AppSymbol/Shortcut(index) separation with Body+Symbol both launching today, sibling (never nested) all-apps targets, TileBody/AppLaunchButton/ShortcutSlot naming, bottom-right except 1×2 bottom-center as one generic rule, and LauncherDimensions tokens. docs/grid.md §12 gives the LauncherTheme snippet; §6–§7 gives the source-order and pipeline.
3. Phases and slices
Phase A — Material colour theming (must land first; everything below depends on it)
Slice A1 — LauncherTheme + static fallback palettes (small)
- What changes: new file core/designsystem/src/main/java/me/foxtails/lakehead/core/designsystem/LauncherTheme.kt (:core:designsystem only). Implements LauncherTheme(darkTheme, content) exactly per docs/grid.md §12: dynamicDarkColorScheme(context) / dynamicLightColorScheme(context) on API 31+, else LauncherFallbackDarkColors / LauncherFallbackLightColors statically defined in the same file (or LauncherFallbackPalettes.kt if preferred). Uses androidx.compose.material3.MaterialTheme(colorScheme=…) for colour scheme only — no Material Card, Scaffold, NavigationBar, FAB, SearchBar, chips. No feature/app changes in this slice.
- Acceptance: ./gradlew :core:designsystem:testDebugUnitTest passes; new unit test asserts fallback palettes are distinct objects, light differs from dark, and API<31 path never calls dynamic scheme (Robolectric or version-gate fake); compose_audit.py shows no new stock product component. Screenshot/manual check deferred to A3.
- Dependencies: none.
- Complexity: small.
Slice A2 — TileColorRole + semantic tile resolution (medium)
- What changes: new core/designsystem/.../TileColorRole.kt (enum Normal, Accent; persisted-role type, never RGB). Edit core/designsystem/.../AppTile.kt only: extend AppTileState with colorRole: TileColorRole = Normal; resolve containerColor/contentColor/borderColor from MaterialTheme.colorScheme (surfaceContainer/primaryContainer/onSurface/onPrimaryContainer or tuned equivalents documented in a comment citing docs/grid.md §12); delete the hardcoded ContainerColor/SelectedContainerColor/PressedContainerColor/SelectedBorderColor/ContentColor constants (keep PressedStateDescription and pressed/selected state handling). Update ResizeCandidateOverlay.kt and MoveCandidateOverlay (in HomeCanvasGrid.kt) usages of AppTileDefaults.TileShape/colours to the new semantic resolution — colour-only, no geometry change.
- Acceptance: existing AppTile tests updated to run inside LauncherTheme (light + dark) and assert Normal≠Accent containers and on* contrast roles differ; ./gradlew :core:designsystem:testDebugUnitTest green; no Color(0xFF…) remains in AppTile.kt.
- Dependencies: A1.
- Complexity: medium.
Slice A3 — Theme wiring + launcher background (small)
- What changes: app/.../MainActivity.kt (:app wiring only): wrap setContent { LauncherTheme { LauncherShell(…) } }. app/.../LauncherShell.kt: root Box gains .background(MaterialTheme.colorScheme.background); stub destinations and pill keep Foundation components, only colours come from scheme. Optional: set status/nav scrim to transparent here (no inset-controller logic — that is Phase G).
- Acceptance: device/manual: light mode shows light background + light tiles, dark mode shows dark equivalents; wallpaper-visible empty cells stay transparent (tiles opaque, gaps show background). ./gradlew assembleDebug + lint clean.
- Dependencies: A1, A2.
- Complexity: small.
Phase B — Grid reference width + edge spacing (independent of hit targets; needs A3 only for visual check)
Slice B1 — Shared LauncherDimensions tokens (small)
- What changes: new core/designsystem/.../LauncherDimensions.kt (:core:designsystem): data class LauncherDimensions(gridGap, horizontalInset, tilePadding, symbolSize) per docs/grid.md §3 + LocalLauncherDimensions composition local + DefaultLauncherDimensions (gap 8dp, inset 8dp — see B3, tilePadding 8dp, symbolSize per existing 40dp row icon / tile symbol to be fixed in C2). Wire LauncherTheme or LauncherShell to provide it; convert AppTile internal padding(8.dp) / spacedBy to tokens (no behaviour change).
- Acceptance: unit test asserts tile width formula cell*span + gap*(span-1) unchanged when tokens substituted; all existing designsystem tests pass.
- Dependencies: A1 (for provider placement convention only).
- Complexity: small.
Slice B2 — Five-column reference, kept adaptive (small)
- What changes: :core:grid reference constant (GridColumns.NORMAL_PHONE_REFERENCE_COUNT) 6→5 plus matching doc-comment update ("normal-phone reference, never a bound"); app/.../MainActivity.kt: columnsBound 6→5, columns in AddToHomeCoordinator 6→5, and any minimumCellPx retune only if cells go negative on the test device (keep GridViewportAdaptation.columnsFor() logic untouched — folding/wide phones still adapt up/down). Update affected unit-test expectations that assert the old 6 reference. No LazyVerticalGrid, no packing change, no persisted-coordinate rewrite.
- Acceptance: GridViewportAdaptation tests: narrow viewport adapts <5, reference phone yields 5, wide yields >5; SquareCellGeometry tests with 5 columns; ./gradlew :core:grid:test green. Device: normal phone shows five square 1×1 cells across.
- Dependencies: B1 (token gap value used in geometry verification).
- Complexity: small.
Slice B3 — 8dp edge = 8dp gap + grid centering (small)
- **Discovery note (2026-09-25):** B3 requires **both** the padding 16dp→8dp change **and** a centered originX fix. The prior assumption (padding-only) was incomplete. `SquareCellGeometry.fromViewport` subtracts `2*padding` for cell size but discards the padding origin; `HomeCanvasGrid` passes no `originX` to `WindowedSpanContainer` (defaults to 0), so `TileBounds` left for column 0 is `x=0` against the screen edge. Right slack = `2*padding + remainder`. `LauncherShell`/`HomeCanvas` add no centering.
- What changes: (1) app/.../MainActivity.kt: horizontalPaddingPx 16dp→8dp in both homeViewportStyle and HomeRouteViewport inputs (so `SquareCellGeometry.fromViewport` computes `cell = (width − 2*8dp − 4*gap)/5`). (2) Pass centered `originX = (availableWidth − gridWidth) / 2` through `WindowedSpanContainer`/`TileBounds`/overlay/drag math — affected call sites at high level:
  - `HomeCanvasGrid` — compute and forward `originX` into `WindowedSpanContainer`.
  - `WindowedSpanContainer` / `TileBounds` — accept `originX` origin offset so tile left = `originX + col * (cell + gap)`.
  - `ResizeCandidateOverlay` — accept and apply `originX` param for correct overlay alignment.
  - `MoveDragMath` / `ResizeDragMath` — factor `originX` origin offset into drag hit-testing and snap geometry.
  - Update screenshot/golden expectations if any.
- Acceptance: device/screenshot: outermost tile edge-to-screen distance equals inter-tile gap (ruler check 8dp); grid is centered with equal left/right slack; lint + assembleDebug clean.
- Dependencies: B2.
- Complexity: small.
Phase C — Square tiles + generic symbol placement (needs A2; independent of hit-target dispatch)
Slice C1 — Square / rectangular tiles (small)
- What changes: core/designsystem/.../AppTile.kt: TileShape RoundedCornerShape(12.dp) → RectangleShape (keep a single TileShape constant so overlays stay consistent). ResizeCandidateOverlay.kt + HomeCanvasGrid.kt MoveCandidateOverlay pick it up with no edits beyond the shared constant if already referencing it; if hardcoding 12dp elsewhere, point them at the constant in this slice.
- Acceptance: designsystem screenshot/paparazzi or semantics test asserts TileShape == RectangleShape; device: home tiles have sharp corners at all spans (1×1, 2×1, 1×2, 2×2, 4×2).
- Dependencies: A2.
- Complexity: small.
Slice C2 — Shared AppSymbol + one generic placement rule (medium)
- What changes: new core/designsystem/.../AppSymbol.kt: @Composable AppSymbol(bitmap, modifier) rendering Image(bitmap, contentScale=Fit, size=LocalLauncherDimensions.current.symbolSize) — draws pre-resolved snapshots only, no LauncherApps/PackageManager/decode (per architecture.md:16). New pure helper symbolAlignment(span: GridSpan): Alignment = if (columns==1 && rows==2) BottomCenter else BottomEnd (exact docs/grid.md §4 rule, generic over future spans). Edit AppTile.kt layout to anchor the icon slot via this rule instead of centering (wide spans keep label-beside-icon arrangement, symbol still bottom-anchored within its region). Edit HomeCanvasTile.kt (:feature:home) to use AppSymbol with HOME_TILE_ICON_TAG.
- Acceptance: unit test: only 1×2 → BottomCenter, every other span in {1×1, 2×1, 2×2, 4×2, 3×2, 4×3, 3×3} → BottomEnd; UI test: 1×2 tile icon centered, 2×2 icon bottom-right, both at symbolSize. No decode/catalogue call in composition (code inspection + existing ownership test).
- Dependencies: B1 (tokens), C1 (shape orthogonal but land together for visual coherence).
- Complexity: medium.
Phase D — Hit targets + home shortcut placeholders (one phase; needs C2)
Slice D1 — TileHitTarget sealed interface (small)
- What changes: new core/designsystem/.../TileHitTarget.kt (or :feature:home if reviewers insist dispatch stays feature-owned — default to designsystem so all-apps can share the vocabulary): sealed interface TileHitTarget { data object Body; data object AppSymbol; data class Shortcut(index: Int) } per docs/grid.md §10. Pure type only, no composable change. Unit test for equality/exhaustive when.
- Acceptance: ./gradlew :core:designsystem:testDebugUnitTest includes exhaustive-when test; no other file references diagnostic colour names (PurpleArea/BlueArea/RedArea grep empty).
- Dependencies: none beyond A-series (naming check in architecture.md:24).
- Complexity: small.
Slice D2 — Home tile Body/AppSymbol as separate sibling regions (medium)
- What changes: core/designsystem/.../AppTile.kt: replace single Box.combinedClickable with sibling layout — TileBody (Box.weight/fillMaxSize.combinedClickable(onClick=onBodyClick, onLongClick=…)) + AppLaunchButton (Box.clickable(onClick=onSymbolClick) wrapping the AppSymbol slot) + ShortcutSlot row (empty in this slice). Semantic names only, never mockup colours. feature/home/.../HomeCanvasTile.kt: pass onBodyClick and onSymbolClick that both call onLaunchApp(identity) today (documented "diverge later: Body → notification surface" per grid.md §10), keep long-press→edit on Body only, keep unavailable no-op + "Unavailable" description, keep isSelected border + "Editing row R column C" description, keep ≥48dp minima and merged-descendant semantics per region (never one parent clickable containing nested buttons).
- Acceptance: UI tests: Body click launches exact identity; symbol click launches same identity via a different node (two distinct Role.Button nodes per tile); TalkBack order Body→Symbol→slots; long-press on symbol does not enter edit; unavailable tile has no launch node. compose_audit.py flags no nested-clickable violation.
- Dependencies: D1, C2.
- Complexity: medium.
Slice D3 — Home shortcut placeholder slots, visual-only (small)
- What changes: AppTile.kt + HomeCanvasTile.kt: render N ShortcutSlot placeholder glyphs (boat-wheel or neutral dot — document as temporary per grid.md §8) in the bottom strip beside the symbol (layout per grid.md §10 diagram: shortcuts left, symbol right). Slots are non-clickable, non-focusable (enabled=false / no clickable modifier) with contentDescription "Shortcut placeholder ${i+1}" and ≥48dp visual box only where space allows (1×1 shows zero slots by default — shortcuts = emptyList() default per grid.md §8). Backed by a shortcutPlaceholders: Int = 0 param fed from test/mock data only; no ConfiguredShortcut persistence, no platform:shortcuts wiring, no click handling in this slice.
- Acceptance: UI test: tile with placeholders=2 shows two placeholder nodes that do nothing when tapped (no launch, no callback); default tile shows none; grep ConfiguredShortcut still docs-only outside comments.
- Dependencies: D2.
- Complexity: small.
Phase E — All-apps rows (somewhat independent; needs A + C2 for colours/symbols, D1 for vocabulary)
Slice E1 — Row geometry + sibling-structure conformance (small)
- What changes: feature/allapps/.../AllAppsRow.kt only: enforce uniform row geometry from tokens (symbol left at symbolSize, label beside, slots right, consistent vertical padding), replace raw Image(...size(40.dp)) with shared AppSymbol, replace BasicText colours with scheme onSurface, keep existing sibling pattern (launch Row vs trailing control) and refuse any parent-clickable refactor. No slot-count change in this slice.
- Acceptance: UI test: every row same height, symbol size equals token, launch region and trailing region are siblings (assertion via two separate Role.Button nodes); existing Add/Added tests still pass.
- Dependencies: A2, B1, C2.
- Complexity: small.
Slice E2 — All-apps shortcut placeholders: leftmost add, rightmost remove (medium)
- What changes: AllAppsRow.kt (+ AllAppsEntry if a slot-count field is needed — prefer alreadyOnHome: Boolean already present, no new persistence): trailing cluster becomes [AddSlot][RemoveSlot] placeholder glyphs (visual only in this task): leftmost slot contentDescription "Add {label} to Home", rightmost "Remove {label} from Home". Reuse the existing onAddToHome callback for the left slot (real behaviour preserved); the right slot is visual-only, disabled in this task (no remove-from-home implementation — explicitly deferred). Keep sibling rule: launch region + each slot are siblings, never nested. Keep honest Added marker semantics until the remove slice lands in a follow-up task.
- Acceptance: UI test: not-on-home row exposes launch node + enabled Add slot (tap → onAddToHome with exact identity) + disabled Remove placeholder (tap → nothing); already-on-home row exposes launch + disabled Remove placeholder + honest marker; no nested-clickable lint warning.
- Dependencies: E1, D1.
- Complexity: medium.
Phase F — Symbol pipeline hardening (separate concern from hit targets; needs C2)
Slice F1 — Source resolver: monochrome → foreground → legacy (medium)
- What changes: :platform:launcherapps only: new AppSymbolSourceResolver (sealed Monochrome/AdaptiveForeground/Legacy) + version-safe helpers (AdaptiveIconDrawable.monochrome only on API 33+, foreground on API 26+), exactly per docs/grid.md §6 snippet. Called from LauncherAppsIconActivityLookup / LauncherAppsIconSnapshotRefresher path off the UI thread; features untouched. Unit/instrumentation tests with fake drawables per API level.
- Acceptance: tests: API33+ with monochrome → monochrome; without → foreground; API26-32 adaptive → foreground; legacy → itself; no resolver call on composition thread (call-site inspection). Bounded PNG cache behaviour unchanged.
- Dependencies: C2 (consumer contract).
- Complexity: medium.
Slice F2 — Normalization + tint + bounded cache (medium)
- What changes: :platform:launcherapps only: bounds/padding normalization (trim transparent insets, fit to square box), monochrome tinting with scheme onSurface equivalent resolved at draw time (never persisted), reuse of existing AppIconSnapshotCache(maxEntries=64) eviction (no second cache). HomeCanvasTile/AllAppsRow automatically benefit via AppSymbol with no edits in this slice.
- Acceptance: screenshot/golden: same app's monochrome, foreground, legacy sources occupy identical visual boxes; tint follows light/dark scheme; cache-eviction test still passes; no decode in composables.
- Dependencies: F1.
- Complexity: medium.
Phase G — Immersive mode (independent; last)
Slice G1 — enterImmersiveMode + lifecycle restore (small)
- What changes: app/.../MainActivity.kt only: private Activity.enterImmersiveMode() using WindowCompat.setDecorFitsSystemWindows(window, false) + WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE + hide(systemBars()) per docs/immersive.md; called from onCreate (before setContent), onResume, and onWindowFocusChanged(hasFocus) when true. Add androidx.core:core dependency if missing. No deprecated SYSTEM_UI_FLAG_*. No per-screen logic.
- Acceptance: device: status + nav bars hidden on launch, swipe from edge transiently reveals then auto-hides, returning to launcher re-hides; lint clean; no test-device claim without install + observation record.
- Dependencies: none (land last to avoid masking visual verification of A–F).
- Complexity: small.
Slice G2 — Edge-to-edge layout + pill safe inset (small)
- What changes: app/.../LauncherShell.kt (+ MainActivity viewport inputs if remeasured): grid/background extend under system bars (fillMaxSize, no permanent bar padding); NavigationControlPill keeps configurable bottom safety inset from WindowInsets.systemGestures so buttons never fight back/home gestures; All Apps bottom clearance (PILL_CLEARANCE_PADDING) recomputed as max(80.dp, gestureInset). Grid width stays windowWidth (not minus bar heights) per immersive.md.
- Acceptance: device: grid extends edge-to-edge under transient bars; pill always tappable above gesture zone; rotation/fold still adapts via B2.
- Dependencies: G1, B2, B3.
- Complexity: small.
4. Exit criteria for the whole task
1. LauncherTheme provides dynamic colours on API 31+ and static fallback below; background, Normal (surfaceContainer/onSurface), and Accent (primaryContainer/onPrimaryContainer) roles resolve from scheme; no hardcoded tile RGB remains.
2. Normal phone shows five square 1×1 cells across (adaptive up/down preserved — folding/wide verified by unit test, not hardcoded); edge inset equals inter-tile gap (8dp everywhere); grid is centered with equal left/right slack; cell/span math unchanged.
3. Tiles are square/rectangular; every span places its symbol bottom-right except 1×2 bottom-center via one generic rule.
4. Home tiles expose Body / AppSymbol / Shortcut(i) as separate sibling regions (Body + Symbol both launch today, documented to diverge); shortcut slots are visual-only placeholders, absent by default.
5. All-apps rows are uniform sibling geometry sharing AppSymbol; leftmost trailing slot is Add (wired), rightmost is Remove (visual-only placeholder in this task).
6. Symbol pipeline prefers monochrome → adaptive foreground → legacy, normalizes/tints/caches off the UI thread; composables render snapshots only.
7. Immersive transient-by-swipe hides status + nav, restores on resume/focus, layout is edge-to-edge with a gesture-safe pill; no deprecated flags.
8. docs/grid.md acceptance items 8–20 hold except real shortcut execution (explicitly deferred); docs/design.md + docs/architecture.md:24 naming/ownership hold (TileBody/AppLaunchButton/ShortcutSlot, no PurpleArea etc., no stock Material product components, no feature→feature deps, no Android/Compose in core:model/Room).
9. Validation per slice recorded: ./gradlew assembleDebug, ./gradlew test, ./gradlew lint, repo_map.py, module_graph.py, function_audit.py, compose_audit.py, plus focused module tests and one device install observation (model/API, HOME selection, light/dark, 5-column, immersive swipe).
5. Explicitly out of scope (do not expand into these)
- Executing home-tile shortcut buttons (req. 10) or the all-apps Remove action — placeholders only; real platform:shortcuts gateway, ConfiguredShortcut persistence/TileAppearance migration, and long-press shortcut configuration belong to a follow-up task.
- Persisting resolved colours, icon bitmaps/drawables, UserHandle, or pixels — persist TileColorRole only (future TileAppearance migration), per architecture.md:26.
- Notification surface behind Body, folders, widgets, search, message center, grouped all-apps, work-profile grouping/badging, usage/contacts/SAF grants — no new permission or destination in this task.
- Changing placement legality, packing, GridViewportAdaptation logic, or rewriting persisted coordinates for new widths.
- Introducing platform:symbols, data:*, or any new Gradle module — pipeline stays in :platform:launcherapps until the documented split trigger is met.
- Replacing Foundation primitives with Material Card/ListItem/NavigationBar/FAB/SearchBar/chips, or encoding diagnostic mockup colours as layout concepts.
- Permanent kiosk lock-task suppression of system UI — transient-by-swipe only.
