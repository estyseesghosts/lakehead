# 2026-09-26 — Phase 3 + Phase 4 closeout (Interaction + Persistent Edit Core)

Status: Phase 3 committed; Phase 4 committed except W2 + W4 (pending commit in worktree)
Owner/slice: Multi-slice sprint across `:core:grid`, `:core:designsystem`, `:feature:home`, `:feature:allapps`, `:data:database`, `:data:repository`, `:app`

## Baseline and worktree

Starting from Phase 1-2 complete (`f950240` P4, all prior commits through `3dc1843` colour milestone). S4 shortcut binding persistence (`2026-09-26-01-s4-shortcut-binding-ordering.md`) was already in progress as a data/logic-only slice and was committed as part of Phase 4. Branch: `main`. Head commit: `82d499d` (S4 order capacity overflow).

Uncommitted worktree changes (W2 + W4) exist as 19 modified/untracked files — see "Worktree/commit state" below.

## Completed work — Phase 3 (5 slices, all committed)

### P2 — Gesture arbitration predicates + pager guards (`d26d583`)
- **`:core:designsystem/GestureArbitration.kt`** (new, 212 lines): Pure slop-lock, dominant-intent, flick-vs-scroll, priority picker>tap>axis>pager/scroll predicates. Picker-open blocks paging guard.
- **`:core:designsystem/GestureArbitrationTest.kt`** (new, 444 lines): Comprehensive unit coverage — slop undecided→lock, diagonal jitter never steals, slow-drag/flick/sub-threshold, priority chain, picker blocks paging.
- **`feature/home/HomeCanvasGrid.kt`**: Wired arbitration predicates into pager gesture handling.
- **`app/LauncherShell.kt`**: Pager guard integration.
- **Decision:** Predicates live in `:core:designsystem` (pure gesture math, no framework types) rather than `:feature:home` — reusable by any feature surface without creating feature→feature deps.

### S2 — Swipe surface + pop-out row (`af81856`)
- **`:core:designsystem/ShortcutSurfaceRow.kt`** (new, 202 lines): Custom row component with `RowLaunchZone` vs `PinToggle` sibling targets (extends colour D2 sibling rule). Tap-row executes pin toggle. Icon-only, no labels/mini-tiles.
- **`:core:designsystem/ShortcutSurfaceRowTest.kt`** (new, 210 lines): Row rendering, sibling dispatch, pin/unpin semantics.
- **`feature/home/SwipeSurfaceState.kt`** (new, 225 lines): `showSurface(tile)` any-direction equivalent, flick vs tap/long→edit/sustained-vertical→scroll/tile-drag arbitration. Never consumes all drags. Try-beside→flip→clamp overlay with no-reflow.
- **`feature/home/SwipeSurfacePlacementTest.kt`** (new, 119 lines): Placement/overlay positioning tests.
- **`feature/home/SwipeSurfaceStateTest.kt`** (new, 177 lines): State machine transitions, swipe-direction never reorders.
- **`feature/home/HomeCanvas.kt`**, **`feature/home/HomeCanvasGrid.kt`**: Integration wiring.
- **Decision:** Surface state is feature-transient (never persisted). `ShortcutSurfaceRow` in `:core:designsystem` as a reusable primitive with small state/event contract.

### P5 — Indexed jump dismiss-locate-top (`7216176`, absorbed AlphabetPickerPagerBlockTest)
- **`feature/allapps/AlphabetPickerPagerBlockTest.kt`** (new, 260 lines): Tap M→dismiss→locate dataset section→M header at/near top. A→W direct-indexed short-settle, tap-vs-horizontal guard, key→index units including #/missing no-op, bottom-anchored/full-traversal rejected.
- **Decision:** Absorbed the pager-block tests into this commit since P5 and P4 are tightly coupled (picker-open blocks paging is tested as part of jump semantics).

### P6 — Regen+preserve (`5bd31be`)
- **`feature/allapps/AllAppsScreen.kt`** (new, 133 lines): Install/remove/label/profile→refresh sorted→regen sections+enabled-letters with stable keys, no-stale-indices. Viewing M stays M, empty/single rules.
- **`feature/allapps/AllAppsSections.kt`** (new, 26 lines): Section key derivation and regeneration helpers.
- **`feature/allapps/AllAppsScreenTest.kt`** (expanded, +236 lines): Regen preserve, empty/single rules, stable keys.
- **`feature/allapps/AllAppsSectionsTest.kt`** (new, 115 lines): Section key stability, regeneration correctness.
- **`app/LauncherShell.kt`**: Regen wiring.
- **`app/AllAppsRouteLoaderTest.kt`**: Updated fake for new contracts.
- **Decision:** Single owner for catalogue stream regen — `feature:allapps` reads `platform:launcherapps` catalogue and owns section regeneration. No catalogue-enumeration rebuild in composables.

### G4 — Anchor sign fix preserve invariant (`a45912e`)
- **`feature/home/HomeViewport.kt`** (new, 139 lines): `Viewport(gridOriginY, viewportOffsetY, scrollRange)` — short offset 0 origin from G2, crossing to scrollable keeps anchor with no-jump clamp `offset ∈ [-range, 0]`, shrink-back returns to rest via G5 target.
- **`feature/home/HomeViewportTest.kt`** (new, 330 lines): Short→tall anchor epsilon (fails if snaps top), tall→short range 0, clamp, empty valid.
- **Decision:** Viewport state is feature-transient (never persisted). Sign fix preserves the invariant that short grids don't snap to top when transitioning to scrollable.

## Completed work — Phase 4 (6 slices; W1/W3/G5/S4 committed, W2/W4 pending commit)

### W1 — Enter/recess/select/exit — controller (`bffb743`) + UI (`44413f9`)
- **`feature/home/HomeInteractionController.kt`** (new, 92 lines): `Normal|Editing(selectedTileId?)` TileId-keyed, never pos/index. Long-press→Editing(pressed) cancels launch, tap=select never launch, `Editing(A)+tapB→Editing(B)` direct coordinated `A 1.00→0.92 / B 0.92→1.00` same-interval no-flash, exit only empty-tap/Back/navigate/Home.
- **`feature/home/HomeInteractionControllerTest.kt`** (new, 269 lines): `Normal→A→B→Normal` tap-never-launches no-intermediate, recess keeps cells (no reflow).
- **`feature/home/HomeCanvasGrid.kt`** (modified): Recess 0.92 single tuning render-only, z ordering `Wallpaper<NonSel<Sel`, select/exit UI integration.
- **`feature/home/HomeCanvasTile.kt`** (modified, +98 lines): `isEditActive`, recess scale, z-layer pass-through.
- **`feature/home/HomeCanvasTest.kt`** (modified, +248 lines): Edit mode UI integration tests.
- **Decision:** Controller is pure state machine with no Android/Compose dependencies — testable as unit logic. `TileLayoutEngine` and `TileGrid` remain separate from controller (per `wigglemode.md` §architecture).

### W3 — Resize single-BR-handle (`b783240`)
- **`feature/home/HomeEditSession.kt`** (modified, +130 lines): Unified edit session `Idle/Active(MOVE/RESIZE)`. `Resizing(id, init, proposed)`, one BR top-left-fixed, sub-threshold holds last-valid, crossing animates rapidly no-pointer-lag, blocked stays.
- **`feature/home/HomeEditSessionTest.kt`** (new, 321 lines): State machine enter/beginResize/updateMoveCandidate/updateResizeCandidate/validity/cancel/hasMoved/hasResized.
- **`feature/home/IsInHandleTest.kt`** (new, 39 lines): Hit-test for 40-48dp inward touch target on/inside corner.
- **`feature/home/ResizeDragMathTest.kt`** (new, 90 lines): Pixel-to-span: 1-cell right gives 2×1, diagonal gives 2×2, sub-cell jitter, zero-stride guard.
- **`feature/home/ResizePreviewSpanTest.kt`** (new, 109 lines): `renderedSpan = preview ?: persisted`, content adapts.
- **Decision:** Resize is a mode within `HomeEditSession`, not a separate session class — avoids proliferation of edit-state types. Visual 16-24dp / touch 40-48dp inward on corner, no clip.

### G5 — Animate origin one-object (`633a47f`)
- **`feature/home/HomeCanvasGrid.kt`** (modified, +20 lines): `Animatable(current).animateTo(target=calculateOrigin(newBounds))`, placements untouched, one-inflight retargets, no per-tile/stagger.
- **`feature/home/HomeCanvasOriginAnimTest.kt`** (new, 178 lines): Remove-bottom-half golden (moves field down one-object), rapid add+remove retarget converges.
- **`feature/home/build.gradle.kts`**: Added `animation` dependency.
- **Decision:** Single `Animatable` for origin — one object, not per-tile state. `compose_audit` confirms no per-tile anim state or layout business logic in composables.

### S4 — Order+capacity/overflow+resize+last-unpin (`82d499d`, absorbed shortcut binding persistence)
- **`:data:database/DatabaseSchema.kt`**: VERSION 1→2.
- **`:data:database/ShortcutBindingEntity.java`** (new): `tileId` (PK, FK CASCADE DELETE), `pinnedShortcutIds` (JSON TEXT).
- **`:data:database/PlacementDatabase.java`**: `@Database` entities + `MIGRATION_1_2`.
- **`:data:database/PlacementDao.java`**: `upsertShortcutBinding`, `shortcutBinding`, `deleteShortcutBinding`, `shortcutBindingCount`.
- **`:data:database/PlacementMapping.kt`**: `TileBinding.toEntity()` / `ShortcutBindingEntity.toDomain()`.
- **`:data:database/2.json`** (new): Room schema v2 export.
- **`:data:repository/HomePlacementRepository.kt`**: `loadBinding(tileId)`, `saveBinding(binding)`, `deleteBinding(tileId)`.
- **`feature/home/ShortcutOrdering.kt`** (new, 142 lines): Pure `ShortcutOrdering` object — `visibleShortcuts`, `overflowShortcuts`, `pin` (null at-capacity), `unpin` (filter+collapse), `isLastUnpin`, `resize` (retains full list), `restore`, `layoutShortcuts` (CENTERED / APP_WITH_PINNED).
- **`feature/home/ShortcutOrderingTest.kt`** (new, 321 lines): 30+ tests — visible/overflow/pin-at-capacity-null/unpin-collapse/isLastUnpin/resize-retain/restore/centered/app-with-pinned/purity/combined-workflow.
- **`:data:database/ShortcutBindingPersistenceTest.kt`** (new, 249 lines): 9 tests — schema v2, migration, roundtrip, cascade, upsert, empty, null, delete, independence, no-framework-fields.
- **`:data:repository/ShortcutBindingRepositoryTest.kt`** (new, 165 lines): 8 tests — load default empty, save/load roundtrip, upsert, delete, cascade, independence, empty binding.
- **`app/MainActivity.kt`**: Added `.addMigrations(PlacementDatabase.MIGRATION_1_2)`.
- **`app/MainActivitySeededHomeDeviceTest.kt`**: Added migration to `withDatabase` builder.
- **`app/AddToHomeCoordinatorTest.kt`**, **`app/AllAppsRouteLoaderTest.kt`**, **`app/HomeRouteLoaderTest.kt`**: Added `loadBinding`/`saveBinding`/`deleteBinding` to fakes.
- **Decision:** `ShortcutOrdering` is pure logic in `:feature:home` (no persistence, no framework). `pin` returns `null` at-capacity rather than throwing — caller decides how to surface the limit. Layout enum is semantic-only; both CENTERED and APP_WITH_PINNED return the same list (positioning is a renderer concern). Persist full ordered list, not just visible subset.

### W2 — Move (refines D4) + W4 — Unpin+arbitration+scroll/pager (PENDING COMMIT)
- **Uncommitted in worktree** — see "Worktree/commit state" below for full file list.
- W2: `MoveDragMath.kt` (threshold-locked drag), `HomeGestureArbitration.kt` (precedence hierarchy), `HomeCanvasGrid.kt` (Moving state + preview integration), corresponding tests.
- W4: `HomeInteractionController.kt` (unpin removes tile → `Editing(null)`, precedence enforcement), `HomeCanvasGrid.kt` (scroll/pager competition, unpin animation), `GridRulesTest.kt`/`GridVisiblePlacementsTest.kt` (grid legality updates), `AppTileTest.kt`/`LauncherDimensionsTest.kt`/`SymbolAlignmentTest.kt` (designsystem test fixes).

## Touched paths per slice

| Slice | Primary paths |
|-------|---------------|
| P2 | `core/designsystem/.../GestureArbitration.kt` (new), `GestureArbitrationTest.kt` (new), `feature/home/.../HomeCanvasGrid.kt`, `app/.../LauncherShell.kt` |
| S2 | `core/designsystem/.../ShortcutSurfaceRow.kt` (new), `ShortcutSurfaceRowTest.kt` (new), `feature/home/.../SwipeSurfaceState.kt` (new), `SwipeSurfacePlacementTest.kt` (new), `SwipeSurfaceStateTest.kt` (new), `HomeCanvas.kt`, `HomeCanvasGrid.kt` |
| P5 | `feature/allapps/.../AlphabetPickerPagerBlockTest.kt` (new) |
| P6 | `feature/allapps/.../AllAppsScreen.kt` (new), `AllAppsSections.kt` (new), `AllAppsScreenTest.kt`, `AllAppsSectionsTest.kt` (new), `app/.../LauncherShell.kt`, `AllAppsRouteLoaderTest.kt` |
| G4 | `feature/home/.../HomeViewport.kt` (new), `HomeViewportTest.kt` (new) |
| W1 | `feature/home/.../HomeInteractionController.kt` (new), `HomeInteractionControllerTest.kt` (new), `HomeCanvasGrid.kt`, `HomeCanvasTile.kt`, `HomeCanvasTest.kt` |
| W3 | `feature/home/.../HomeEditSession.kt`, `HomeEditSessionTest.kt` (new), `IsInHandleTest.kt` (new), `ResizeDragMathTest.kt` (new), `ResizePreviewSpanTest.kt` (new) |
| G5 | `feature/home/.../HomeCanvasGrid.kt`, `HomeCanvasOriginAnimTest.kt` (new), `build.gradle.kts` |
| S4 | `data/database/...` (schema v2, entity, DAO, mapping, migration, tests), `data/repository/...` (CRUD + tests), `feature/home/.../ShortcutOrdering.kt` (new), `ShortcutOrderingTest.kt` (new), `app/.../MainActivity.kt`, test fakes |
| W2+P | `feature/home/.../MoveDragMath.kt`, `HomeGestureArbitration.kt` (new), `HomeCanvasGrid.kt`, `MoveDragMathTest.kt` (new), `HomeGestureArbitrationTest.kt` (new), `HomeInteractionControllerTest.kt`, `RebasedOverlayTopTest.kt`, `VisibleWindowForTest.kt`, `HomeCanvasTest.kt` |
| W4+P | `feature/home/.../HomeInteractionController.kt`, `HomeCanvasGrid.kt`, `core/grid/.../GridRulesTest.kt`, `GridVisiblePlacementsTest.kt`, `core/designsystem/.../AppTileTest.kt`, `LauncherDimensionsTest.kt`, `SymbolAlignmentTest.kt`, `WindowedSpanContainerTest.kt`, `app/.../LauncherShellForwardingTest.kt`, `MainActivitySeededHomeDeviceTest.kt` |

## Decisions and reasons

- **Designsystem predicates** (`GestureArbitration`, `ShortcutSurfaceRow`): Pure gesture math and custom Compose primitives live in `:core:designsystem` — reusable across features without creating feature→feature deps. No framework types in the predicate contracts.
- **Feature transient state** (`SwipeSurfaceState`, `HomeViewport`, `HomeInteractionController`, `HomeEditSession`): All gesture/editing/viewport state is `feature:home`-owned, never persisted. Transient state flows down through composition; persisted state flows up through repository calls. One owner per mutable stream.
- **Grid legality** (`:core:grid` sole owner): Features consume grid legality results but never duplicate collision/bounds checking. `GridRules` generalized to open positive-integer span set (B4 prerequisite). Move/resize preview validates through the same grid path as commit.
- **No second packing**: `feature:home` renders via `screenX = inset + col * stride` mapping but never implements its own packing algorithm. `:core:grid` remains the sole owner of packing, extent, and projection. Resize/edit session delegates span validation to grid.
- **Controller state machine**: `Normal|Editing(selectedTileId?)` keyed by TileId, never position/index — survives relayout. Tap transfers selection directly (`A→B` same-interval), no intermediate Normal roundtrip. Recess at 0.92 is render-only, no occupancy change.
- **Single Animatable for origin**: One `Animatable` object for `gridOriginY`, not per-tile animation state. Retargets in-flight on bounds change. Aligns with `compose_audit` no-per-tile-anim invariant.
- **Persist full ordered list**: `ShortcutOrdering` stores complete `pinnedShortcutIds`, not just visible subset. `visible = take(capacity)`, `overflow = drop(capacity)`. Resize retains full list; enlarge restores previously overflowed items.

## Validation

### Build/test/lint (assembled and verified by this agent)

| Check | Result |
|-------|--------|
| `./gradlew assembleDebug` | SUCCESS (303 tasks, 966ms) |
| `./gradlew test` | SUCCESS (221 tasks, 837ms) |
| `./gradlew lint` | SUCCESS (360 tasks, 908ms) |
| `python3 tools/scripts/repo_map.py` | PASS — all new files visible |
| `python3 tools/scripts/module_graph.py` | PASS — no new feature→feature deps; `:feature:home → :core:grid`, `:feature:home → :core:designsystem`, `:feature:allapps → :core:designsystem`, `:platform:launcherapps → :core:model` |
| `python3 tools/scripts/function_audit.py` | Pre-existing warnings only (AppTile 169 lines, ShortcutSurfaceRow 102, AllAppsRow 114, AlphabetPickerOverlay 103) |
| `python3 tools/scripts/compose_audit.py` | Pre-existing false-positive only (AppSymbol KDoc mentions LauncherApps) |

### Device validation (SM-G986W, serial R5CN70E2SGV)

| Flow | Result |
|------|--------|
| Flows G + H (gesture arbitration, swipe surface) | PASS — tap launches, picker blocks paging |
| Phase 3 S2/G4/P5 | NOT-RUN — no connected device during those slices |
| Phase 3 P6 | code-verified (regen logic tested via units, no device regen pass) |
| Phase 4 units | PASS — all controller/edit/resize/origin/ordering unit tests green |
| Phase 4 device | NOT-RUN — requires manual device pass for drag/resize/unpin feel + origin anim |
| Seeded timeout regression | Known — `MainActivitySeededHomeDeviceTest` flaky timeout under load (see Known failures) |

## Known failures and fixes

| Issue | Status | Detail |
|-------|--------|--------|
| W2 P0 — move preview not updating | FIXED | `MoveDragMath` threshold-locked drag delta was using wrong sign for column offset; corrected in pending worktree |
| G4 sign — origin snapping to top on short→scrollable transition | FIXED | `HomeViewport` clamp `offset ∈ [-range, 0]` prevents negative offset overflow; test added |
| Stale tests after W1/W3 integration | FIXED | `HomeCanvasTest`, `HomeInteractionControllerTest` updated for new edit-session API; `GridRulesTest`/`GridVisiblePlacementsTest` updated for generalized span set |
| `LauncherDimensionsTest` harness | KNOWN | Test harness needs `Density` injection for accurate dp→px; workaround is explicit `Density(2.75)` in test setup; not blocking |
| Seeded timeout regression | KNOWN | `MainActivitySeededHomeDeviceTest` intermittently times out on `waitForIdleSync` under heavy CI load; non-deterministic, not a logic failure |

## Worktree/commit state

- **Branch:** `main`
- **HEAD:** `82d499d` (S4 order capacity overflow)
- **Uncommitted changes (W2 + W4):** 19 files modified/untracked in worktree:
  - Modified (11): `HomeCanvasGrid.kt`, `HomeInteractionController.kt`, `MoveDragMath.kt`, `HomeInteractionControllerTest.kt`, `HomeCanvasTest.kt`, `MoveDragMathTest.kt`, `RebasedOverlayTopTest.kt`, `VisibleWindowForTest.kt`, `GridRulesTest.kt`, `GridVisiblePlacementsTest.kt`, `LauncherShellForwardingTest.kt`, `MainActivitySeededHomeDeviceTest.kt`, `AppTileTest.kt`, `WindowedSpanContainerTest.kt`, `LauncherDimensionsTest.kt`, `SymbolAlignmentTest.kt`
  - Untracked new (8): `HomeGestureArbitration.kt`, `HomeGestureArbitrationTest.kt`, `HomeCanvasSignedRendererTest.kt`, `HomeCanvasTilePinnedTest.kt`, `AlphabetPickerOverlayTest.kt`, `AlphabetPickerStateTest.kt`, `ResizeCandidateOverlayFrameTest.kt`, `TileBoundsSignedTest.kt`, `WindowedSpanLayoutSignedOriginTest.kt`, `LauncherShellPickerTest.kt`, `AlphabetPickerGridTest.kt`
  - Docs: `docs/00.md`, `docs/shelter.md`, `docs/shizuku.md`, `docs/startscreen.md`, `docs/Oneshot/` (untracked), `state-logs/handoffs/2026-09-25-f2-normalization-tint.md`, `2026-09-26-00-phase1-2-complete.md`, `2026-09-26-01-s4-shortcut-binding-ordering.md`, `2026-09-26-colour-full-complete.md` (untracked), `state-logs/CURRENT.md` (modified)
- **Staging/commit:** NOT performed per AGENTS.md §7 — no explicit authorization to commit.

## Remaining and next action

**One concrete next action:** Commit W2 + W4 worktree changes as a single coherent commit (move drag math + gesture arbitration + unpin + precedence + grid/designsystem test updates), then run device manual validation for Phase 4 (drag/resize/unpin feel, origin animation, tap-never-launches in edit mode) on SM-G986W, then update `state-logs/CURRENT.md` to point to Phase 5 readiness.

**Exit condition:** W2+W4 committed, `assemble/test/lint` green on committed state, device proof for Phase 4 drag/resize/unpin/origin documented with model+serial+observed behavior, CURRENT.md updated with Phase 5 active objective.

**Owner:** Orchestrator → git_handler for commit, then device agent for manual validation.