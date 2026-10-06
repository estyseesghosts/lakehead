# Lakehead Home Grid Repair — Verified Assessment and Implementation Plan

## Scope

This plan is based on static inspection of the exact uploaded `lakehead.zip` tree and the supplied `x.md` analysis. The repository snapshot is at commit `3b1c59a86f2b2614b28e5371d36f261738dcd463` with additional uncommitted Home-grid work present in the archive.

I attempted to run the relevant Gradle tests, but the wrapper tried to fetch Gradle 9.5 from `services.gradle.org` and the analysis environment has no outbound network. Therefore, every code claim below is grounded in source inspection; runtime behavior still needs to be confirmed in a normal development environment before each migration phase is accepted.

---

# 1. Executive assessment

The supplied analysis is **substantially accurate**. It correctly identifies the central failure: the launcher has accumulated several coordinate systems, multiple mirrors of gesture state, a large edit frontier/auto-scroll state machine, windowed/rebased rendering, and persistence acknowledgement logic inside one enormous Compose function. The result is much harder to reason about than the product requires.

The largest measurements are correct:

- `HomeCanvasGrid.kt`: **3,166 lines**.
- `HomeCanvasGrid()`: **1,882 lines**, spanning roughly lines 178–2059.
- `feature/home/src/main`: about **6,550 Kotlin lines**.
- `core/grid/src/main`: about **1,024 Kotlin lines**.
- `HomeCanvasTest.kt`: about **1,961 lines**.
- `FeedOverlayGatewayImpl.kt`: about **1,938 lines**.

The repair should **not** continue the current patch-by-patch movement-hardening path. The correct move is to freeze a simpler grid contract, migrate behavior to it incrementally, then delete the compatibility machinery.

There are, however, several important qualifications to `x.md`:

1. Signed rows, origin rebasing, origin animation, and row-window rendering are not merely accidental code. They are explicitly reflected in existing design/architecture documents. Removing them is still recommended for this launcher, but that is a **contract reset**, not a no-behavior-change refactor.
2. Persisted Home placements already require `row >= 0`. Negative rows exist only in signed presentation/core scaffolding. The current architecture is internally inconsistent rather than truly using negative persisted Home rows.
3. Row 0 should become the permanent **logical** top, but the visual grid may still be centered in normal mode through a presentation-only offset. The important rule is that this offset must not move underneath a drag.
4. The recommendation to collapse every geometry type into one type goes too far. The domain/core module boundary is useful. Remove redundant signed/presentation geometry and repeated conversions, but do not destroy clean module separation merely to reduce type count.
5. `RenderableSpans` already keeps tile-size policy in the Home UI layer. Renaming it to `AllowedTileSizes` is optional; its architectural separation is already mostly correct.
6. The row-gap normalizer is a repository policy and can remain. It should be isolated from live pointer math and its returned normalized placement set must be treated as authoritative after persistence.

---

# 2. Accuracy review of the supplied analysis

| Supplied claim | Verdict | Codebase check |
|---|---|---|
| Most complexity is in `feature/home`, not the pure grid core. | **Confirmed** | `feature/home` is ~6.5k production lines versus ~1k in `core/grid`; the 3,166-line `HomeCanvasGrid.kt` dominates the feature. |
| `HomeCanvasGrid()` owns far too many responsibilities. | **Confirmed** | It owns scroll state, edit session state, gesture routing, move/resize logic, edge state, frontier state, origin animation, auto-scroll, pending commit leases, overlays and rendering coordination. |
| There is no single coordinate model. | **Confirmed** | Current code combines logical positions, signed placements, `rowOrigin`, rendered origins, animated Y origins, current/frozen scroll values, window origins and edit-frontier bounds. |
| `frozenScrollBaselinePx` algebraically cancels out. | **Confirmed exactly** | `MoveDragMath.cellForPixel()` computes `baseline + (current - baseline)`, which is simply `currentScrollPx`. |
| Move mapping silently clamps invalid requests into legal cells. | **Confirmed** | Candidate row/column are coerced into legal bounds before final validation. This can make a raw out-of-bounds request appear as another valid cell. |
| Grab offset is conceptually correct and should be preserved. | **Confirmed** | Current movement frame stores grab offsets and uses them to derive tile top-left from pointer position. Keep this behavior. |
| Persisted rows are nonnegative while signed-row machinery also exists. | **Confirmed, with nuance** | `LogicalPosition` requires nonnegative row/column; `SignedGridPlacement` allows negative rows. Negative Home rows are not currently persisted, so signed rows are mostly presentation/future scaffolding. |
| Measuring height as `maxRowExclusive - minRow` can erase leading logical space. | **Confirmed and important** | Current vertical measurement/rebasing uses occupied height and `minRow`. A single tile at row 5 can be rendered as a short local region rather than preserving five rows above it. This conflicts with the gap normalizer's explicit preservation of leading empty rows. |
| Windowed/rebased rendering is unnecessary for current Home use. | **Reasonable recommendation, but a contract change** | `GridRowWindow`/`WindowedSpanContainer` are intentionally documented architecture, but Home is effectively their only production consumer. Removing them simplifies the system substantially. |
| Rendering and hit testing reconstruct geometry separately. | **Confirmed** | Move, resize, renderer, overlays and supporting helpers each recreate parts of cell/pixel geometry. |
| Animated origin causes geometry to change during manipulation and forces re-freezing. | **Confirmed** | `HomeCanvasGrid` animates the vertical origin and maintains structural re-freeze/remapping machinery. Keep normal-mode presentation animation only if it is frozen outside active editing. |
| Move and resize share too much state. | **Confirmed** | `HomeEditSession.Active` carries origin/candidate/last-valid position and span data plus `mode`, so each operation carries fields belonging to the other. |
| Gesture routing should be chosen once per drag. | **Confirmed as the better target** | Existing logic has extensive state-dependent routing and recovery. A fixed operation route after gesture slop would remove many impossible/transitional states. |
| Edit workspace size is mixed with logical legality. | **Confirmed** | `legalityRows`, frontier growth and finite row bounds participate in movement validity even though scroll reachability and placement validity are separate concepts. |
| The documented eight-scroll MOVE budget is not enforced. | **Confirmed** | Documentation/comments refer to eight successful scroll changes per generation, but the inspected ticker has no corresponding enforcing counter. |
| Real `ScrollState.maxValue` can lag the synthetic edit maximum. | **Confirmed** | Both comments and compensation code acknowledge that `ScrollState` can still be clamped to the older normal content size while edit logic assumes a larger workspace. |
| Persistence acknowledgement does not belong in the pointer state machine. | **Confirmed** | `PendingMoveCommit`, request sequencing and move acknowledgements live inside/around the grid interaction path. Repository mutation already returns accepted placements, giving a cleaner reconciliation point. |
| There are too many live copies of edit state. | **Confirmed** | `editSession`, live refs, updated-state holders, local session snapshots, move-frame mirrors, edge/baseline state and interaction state overlap in authority. |
| Grid DTO conversion happens repeatedly during pointer updates. | **Confirmed** | Edit validation converts the rendered placement list into grid placements repeatedly instead of taking a stable occupancy snapshot at gesture start. |
| There are too many geometry types. | **Directionally correct** | Some redundancy should go, especially signed/presentation conversions. Keep useful domain/core boundaries rather than replacing everything with one feature-local type. |
| The gap normalizer should not participate in dragging. | **Confirmed** | It is correctly a persisted-layout policy. Keep it post-commit and make reconciliation explicit. |
| Resize-size policy should be UI-specific rather than grid-core policy. | **Already mostly true** | `RenderableSpans` is already Home/UI policy while core rectangle rules remain generic. |
| The function-audit script misses the worst function. | **Confirmed exactly** | The script begins brace tracking at the function declaration; lambda defaults in `HomeCanvasGrid()`'s signature return brace depth to zero before the real body, so the 1,882-line function escapes detection. |
| The repository contains patch-history comments that should be removed after repair. | **Confirmed** | Production grid code contains A/P/W generations, structural re-freeze and other forensic comments. Preserve only durable invariant comments after migration. |

---

# 3. Target grid contract

Freeze these rules before touching implementation. They become the acceptance contract for every slice.

1. **Logical origin is permanent:** column 0, row 0. Persisted rows and columns are never negative.
2. **Horizontal space is bounded by `columns`; vertical logical space is nonnegative and effectively unbounded.** Do not use the temporary visible/edit workspace as a legality bound.
3. **Leading rows are real.** A tile at row 5 has five logical rows above it. Content height is based on `max(row + rowSpan)`, not `maxRow - minRow`.
4. **One immutable `HomeGridMetrics` owns all logical/pixel transforms.** Rendering, hit testing, move, resize and overlays use it.
5. **Scroll is applied exactly once:** viewport Y → content Y is `viewportY + scrollY`.
6. **A visual rest offset may center a short grid, but it is presentation-only.** Freeze it for the entire edit session; never animate the coordinate frame underneath a drag.
7. **Move is absolute, not accumulated:** current pointer + current scroll − immutable grab offset → requested top-left cell.
8. **Move preserves span. Resize preserves position.** These are type/state invariants, not late validation checks.
9. **Invalid requests remain invalid.** Never clamp a raw out-of-bounds requested cell into a different valid cell. Keep the last valid preview if desired.
10. **Collision is logical rectangle intersection only.** Pixel geometry never determines placement legality.
11. **Edit workspace size only controls actual scrollable content height.** It cannot make a logical cell legal or illegal.
12. **One gesture chooses one route.** After gesture slop, it is MOVE or RESIZE until release/cancel.
13. **Persistence starts only after release.** Gesture code emits a commit intent and knows nothing about database acknowledgements.
14. **Repository-normalized accepted placements are authoritative.** If gap normalization moves additional tiles, the state owner reconciles from that accepted result.
15. **Home renders all current tiles directly.** No Home row-window virtualization until real measurements demonstrate a need for it.

---

# 4. Target ownership

The end state should remain small and explicit rather than replacing one god file with dozens of micro-abstractions.

```text
feature/home
├── HomeGrid.kt             // assembly, scrolling, top-level state wiring
├── HomeGridGeometry.kt     // HomeGridMetrics and transforms
├── HomeGridEditor.kt       // selection + Move/Resize state transitions
├── HomeGridGestures.kt     // pointer loop + simple edge auto-scroll
├── HomeGridLayout.kt       // absolute logical placement of tile children
└── HomeGridOverlay.kt      // selection outline, unpin, resize handle, invalid preview

core/grid
├── GridPlacement.kt        // canonical pure logical rectangle types as needed
├── GridRules.kt            // bounds + collision only
└── GridRowGapNormalizer.kt // repository layout policy
```

Do not force exact filenames if existing boundaries make a nearby arrangement cleaner. The ownership rules matter more than file-count cosmetics.

---

# 5. Implementation plan

## Phase 0 — Stop extending the old architecture

### Slice 0.1 — Preserve the current snapshot

**Work**

- Record the current commit and dirty-file list before migration.
- Do not merge/rebase unrelated work into the grid repair.
- Treat the current uncommitted movement hardening as reference material, not as architecture that must be preserved.

**Exit gate**

- Current tree can be reconstructed exactly if a slice must be reverted.

### Slice 0.2 — Supersede the old movement plan

**Files**

- `docs/architecture.md`
- `docs/design.md`
- current grid task/state-log documents

**Work**

- Write the target contract from section 3 into the repository.
- Explicitly supersede the current signed/rebased/windowed Home movement plan.
- State that negative persisted Home rows, drag-time origin animation, Home row-windowing, clamped candidates, edit-legality frontiers and persistence leases are being retired.
- Preserve normal-mode centering as a visual option, not a logical origin.

**Exit gate**

- No active task document tells implementers to add more structural re-freezing, signed Home rows, row-window movement frames or eight-step MOVE generations.

### Slice 0.3 — Fix the complexity guardrail first

**Files**

- `tools/scripts/function_audit.py`
- its tests, if present

**Work**

- Make Kotlin function-body detection skip the full function signature before counting body braces.
- Correctly ignore braces inside lambda defaults, strings and comments.
- Add a regression fixture shaped like `HomeCanvasGrid()`.

**Exit gate**

- The script reports the existing ~1,882-line `HomeCanvasGrid()` before decomposition.
- The test fixture proves lambda default arguments cannot terminate body scanning early.

### Slice 0.4 — Establish a runnable baseline outside this analysis environment

**Work**

Run the existing relevant suite before refactoring. Fix unrelated red tests first rather than carrying an unknown baseline.

**Suggested commands**

```bash
./gradlew :core:grid:test :feature:home:test --no-daemon
./gradlew :app:test --no-daemon
```

**Exit gate**

- Baseline is green or every pre-existing failure is explicitly recorded.

---

# Phase 1 — Make logical row semantics coherent

**Repository status (2026-10-05): completed, slices 1.1–1.4.** Absolute-row regression tests, row-free core/repository legality APIs and callers, row-zero content extent, and Home's absolute placement/origin migration are implemented. Full build, unit tests and lint pass; the existing sparse-row rendering instrumentation test passes on SM-G986W / Android 13. Shared signed/windowed primitives remain for explicit callers; Phase 2+ geometry, clamping, gesture and rendering rewrites are not complete. See `agents/tasks/grid-repair-phase1.md` and `../state-logs/handoffs/2026-10-05-grid-repair-phase1.md` for acceptance and exact evidence.

## Phase 0 — Stop extending the old architecture

**Repository status (2026-10-06): completed, slices 0.1–0.4, executed after Phase 1.** The baseline snapshot is recorded in the Phase 0 handoff; the section 3 contract is now frozen in `architecture.md`, `design.md` and `agents/tasks/grid-repair-phase0.md`; `tools/scripts/function_audit.py` now skips full signatures and ignores braces in comments and literals, and reports the real 1,838-line `HomeCanvasGrid()` under regression test; and `:core:grid`/`:feature:home`/`:app` unit tests were re-executed from a forced rerun and are green. Note that Phase 0 ran *after* Phase 1 in this repository, by user direction.

### Slice 0.1 — Preserve the current snapshot

**Add pure tests for**

- row 0 as permanent logical top;
- tile at row 5 retaining five rows above it;
- content bottom as `max(row + rowSpan)`;
- leading empty rows surviving row-gap normalization;
- no negative persisted row.

**Exit gate**

- Tests describe desired semantics before production changes.

## Slice 1.2 — Separate vertical legality from viewport/workspace size

**Files**

- `core/grid/GridRules.kt`
- repository interfaces/callers currently passing `rows`/`rowsBound`

**Work**

- Change core placement legality to require only:
  - `column >= 0`;
  - `column + width <= columns`;
  - `row >= 0`;
  - positive span;
  - no overlap.
- Remove the edit/frontier row count from logical move/resize validation.
- Prefer removing the public finite row bound entirely. If a storage safety limit must remain temporarily, keep it at the repository boundary and do not expose it as edit workspace geometry.

**Likely affected callers**

- Home route/loading code
- app setup currently using the 4096-row bound
- repository mutation tests
- Home edit validation

**Exit gate**

- Increasing or decreasing the UI workspace height does not change whether the same logical candidate is legal.

## Slice 1.3 — Replace occupied-height semantics

**Files**

- `GridVerticalLayout.kt` / `GridVerticalExtent.kt` or their replacements
- Home projection/layout helpers

**Work**

- Base logical content extent on absolute row 0:

```text
contentRows = max(row + rowSpan)
```

- Stop subtracting `minRow` for Home content height.

**Exit gate**

- A sole tile at row 5 occupies content through row 6+span as appropriate, rather than becoming a locally rebased one-row region.

## Slice 1.4 — Remove Home's signed-row dependency

**Files**

- `SignedGridPlacement.kt`
- `GridContentBounds.kt`
- Home conversion/projection helpers

**Work**

- Migrate Home callers to nonnegative absolute rows.
- Do not delete shared primitives until a production-reference search shows no remaining caller.

**Exit gate**

- Home production code constructs no `SignedGridPlacement` and carries no Home `rowOrigin` derived from `minRow`.

---

# Phase 2 — Create one geometry implementation

## Slice 2.1 — Add `HomeGridMetrics`

**File**

- `feature/home/.../HomeGridGeometry.kt`

**Suggested shape**

```kotlin
data class HomeGridMetrics(
    val columns: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val columnGapPx: Float,
    val rowGapPx: Float,
    val originXPx: Float,
    val restOffsetYPx: Float,
) {
    val columnStridePx: Float
    val rowStridePx: Float

    fun left(column: Int): Float
    fun top(row: Int): Float
    fun width(columnSpan: Int): Float
    fun height(rowSpan: Int): Float
    fun rect(position: LogicalPosition, span: LogicalSpan): Rect
    fun requestedTopLeftCell(contentX: Float, contentY: Float): RequestedCell
}

// Raw pointer result. Deliberately permits negative/out-of-range values until validation.
data class RequestedCell(val column: Int, val row: Int)
```

`RequestedCell` must remain unconstrained so an above-grid or past-edge request can be represented as invalid rather than being clamped or rejected by a persisted-domain constructor. `restOffsetYPx` is a frozen presentation offset, not a second logical row origin.

**Exit gate**

- There is one tested object capable of producing every tile rectangle and performing the inverse top-left mapping.

## Slice 2.2 — Add transform invariant tests

Table-driven tests should cover:

- every column;
- representative rows including 0, 1, 5, 50;
- every supported Home tile span;
- several viewport widths and resulting cell sizes;
- several scroll positions;
- center/corner grab points on large tiles;
- nonzero normal-mode rest offset.

**Required invariants**

```text
logical top-left -> pixel -> logical top-left == original cell
```

and:

```text
stationary viewport pointer + one rowStride of downward scroll
=> requested logical row increases by exactly one
```

**Exit gate**

- All transform behavior can be proven without Compose.

## Slice 2.3 — Replace MOVE coordinate math

**Files**

- `MoveDragMath.kt`
- new geometry file

**Work**

Derive each candidate from absolute current state:

```text
pointerContentX = pointerViewportX
pointerContentY = pointerViewportY + currentScrollY

tileLeft = pointerContentX - grabOffsetX
tileTop  = pointerContentY - grabOffsetY

requestedCell = inverseGridTransform(tileLeft, tileTop)
```

Remove from MOVE math:

- `frozenScrollBaselinePx`;
- `rowOrigin`;
- `legalityRows`;
- structural re-freezing inputs;
- any accumulated pointer-delta placement.

**Snapping**

Define one explicit snap rule in `HomeGridMetrics` and test it. Do not leave floor/rounding behavior scattered between callers.

**Exit gate**

- `frozenScrollBaselinePx + (current - frozenScrollBaselinePx)` no longer exists.
- Candidate position depends only on current pointer, current scroll, immutable grab offset and immutable metrics.

## Slice 2.4 — Stop clamping candidate cells

**Work**

- Return the requested raw cell.
- Validate it separately.
- If it is out of bounds/colliding, mark invalid and retain last-valid preview if that remains the product behavior.

**Exit gate**

- Dragging above row 0 produces an explicit invalid negative request; it does not silently turn into row 0.
- Dragging past the right edge does not silently snap to the last legal column.

## Slice 2.5 — Replace RESIZE geometry with the same metrics

**Work**

- Resize starts from the immutable original tile top-left.
- Pointer determines only requested span.
- Product-supported spans continue to be filtered through `RenderableSpans`.
- Remove independent row-origin/signed coordinate reconstruction from resize math.

**Exit gate**

- All resize tests assert `positionAfter == positionBefore`.
- Move and resize both consume the same `HomeGridMetrics`.

## Slice 2.6 — Eliminate duplicate pixel formulas

Search `feature/home` for direct combinations of cell size, gap, row, column and origin. Migrate rendering, hit testing and overlays to `HomeGridMetrics`.

**Exit gate**

- Outside `HomeGridGeometry.kt` and its tests, production code does not implement a second logical↔pixel formula.

---

# Phase 3 — Replace the edit-state model

## Slice 3.1 — Introduce a single authoritative `HomeEditState`

**Suggested form**

```kotlin
data class HomeEditState(
    val selectedTileId: ItemId? = null,
    val resizeArmed: Boolean = false,
    val drag: DragState = DragState.None,
)

sealed interface DragState {
    data object None : DragState

    data class Move(
        val itemId: ItemId,
        val originalPosition: LogicalPosition,
        val span: LogicalSpan,
        val grabOffsetPx: Offset,
        val requestedCell: RequestedCell,
        val previewPosition: LogicalPosition,
        val validity: CandidateValidity,
        val occupied: GridOccupancy,
    ) : DragState

    data class Resize(
        val itemId: ItemId,
        val position: LogicalPosition,
        val originalSpan: LogicalSpan,
        val requestedSpan: LogicalSpan,
        val previewSpan: LogicalSpan,
        val validity: CandidateValidity,
        val occupied: GridOccupancy,
    ) : DragState
}
```

Exact fields may be reduced further during implementation.

**Exit gate**

- MOVE state cannot mutate span.
- RESIZE state cannot mutate position.
- Selection, resize arming and current drag have one authoritative owner.

## Slice 3.2 — Snapshot occupancy once per drag

**Work**

- At drag start, convert committed placements to the pure grid form once, excluding the manipulated tile.
- Candidate updates validate only against this immutable occupancy snapshot.
- Remove whole-list `toGridPlacements()` conversion from every pointer update.

**Exit gate**

- Candidate update has no O(tile-count) DTO conversion/allocation beyond rectangle collision against the stable occupancy collection.

## Slice 3.3 — Move pure transition logic out of Compose

Create pure operations for:

- enter/leave edit mode;
- arm/disarm resize;
- begin move;
- update move;
- begin resize;
- update resize;
- cancel;
- finish and produce a commit intent.

**Exit gate**

- Most editor behavior is covered by normal Kotlin tests with no Compose runtime.

## Slice 3.4 — Retire `HomeEditSession.Active` and redundant mirrors

After all callers use the new state:

- remove mixed position/span active-session state;
- remove live session copies that existed only for ticker/refreeze support;
- collapse or remove `HomeInteractionController` if it duplicates `HomeEditState`.

**Exit gate**

- A search for “authoritative” comments is no longer needed to explain which edit-state copy wins.

---

# Phase 4 — Replace gesture routing

## Slice 4.1 — Create one readable pointer loop

**File**

- `HomeGridGestures.kt`

**Target flow**

```text
pointer down
  -> hit test using HomeGridMetrics
  -> long-press/edit selection handling
  -> wait for drag slop
  -> choose MOVE or RESIZE once
  -> update that operation until up/cancel
  -> emit commit intent or cancel
```

**Exit gate**

- The main gesture function can be read top-to-bottom without following persistence, row-window or structural-re-freeze callbacks.

## Slice 4.2 — Fix MOVE routing

**Work**

- Preserve exact grab location at gesture start.
- Compute every update from absolute pointer/current scroll.
- Never change operation mode mid-drag.

**Tests**

- left/right/up/down;
- diagonal;
- 1×1 and large tile;
- grab at center and each corner;
- collision;
- row < 0;
- right-edge overflow.

**Exit gate**

- Span is byte-for-byte/logically unchanged across every MOVE test.

## Slice 4.3 — Fix RESIZE routing

**Work**

- Resize only starts from the armed resize handle path.
- Grabbing the handle cannot toggle resize mode off before the drag route is chosen.
- Once resize starts, the route is fixed until release/cancel.

**Exit gate**

- Position is unchanged across every resize path, including invalid candidates and cancellation.

## Slice 4.4 — Isolate pager arbitration

**Work**

- Keep pager-versus-grid gesture ownership as a small outer concern.
- Once a tile drag owns the pointer, pager state must not alter cell math or edit state.

**Exit gate**

- Integration test proves an active tile drag is not stolen by page navigation.

---

# Phase 5 — Replace Home rendering with absolute placement

## Slice 5.1 — Add `HomeGridLayout`

**Work**

Use a simple custom `Layout` or equivalent that receives:

- committed placements;
- preview placement for the active tile;
- one `HomeGridMetrics`.

Place every tile directly at its absolute logical row/column. No row-window filtering and no `minRow` rebasing.

**Exit gate**

- A tile at row N is rendered from row N regardless of what other rows are occupied.

## Slice 5.2 — Make content height truthful

**Work**

- Normal content rows = absolute `maxRowExclusive` from row 0.
- The Compose measured content must actually be this tall.
- Minimum viewport height may still be used to avoid pointless scrolling when all content fits.

**Exit gate**

- Scroll range and visual content extent agree without a synthetic normal maximum.

## Slice 5.3 — Keep centering only as presentation

If short-grid centering is still desired:

- compute `restOffsetYPx` from viewport and absolute content height;
- animate it only while not editing;
- freeze the current value when edit mode begins;
- use that frozen value in both rendering and inverse pointer mapping until editing ends.

**Exit gate**

- Entering a drag cannot move the grid coordinate frame beneath a stationary pointer.

## Slice 5.4 — Move overlays onto the same rectangles

**Work**

- Selection outline, unpin button, resize handle, invalid preview and contextual hit regions all derive their tile bounds through `HomeGridMetrics.rect()`.

**Exit gate**

- No overlay has an independent cell/stride/origin calculation.

## Slice 5.5 — Remove Home row-window rendering

**Work**

- Stop using `GridRowWindow`/`WindowedSpanContainer` from Home.
- Keep the shared primitive temporarily if another production caller remains.

**Exit gate**

- Home composes all current Home tiles directly.
- No movement code carries `windowFirstRow`, signed renderer origin or overscan-window state.

---

# Phase 6 — Rebuild scrolling and auto-scroll from actual content

## Slice 6.1 — Add one transient edit workspace extent

**State**

```text
workspaceBottomRow
```

**Work**

- Initialize from committed content bottom plus enough room for the visible edit area.
- It controls only measured content height.
- Extending it does not touch GridRules or candidate validity.

**Exit gate**

- A candidate at row 30 is logically the same candidate before and after `workspaceBottomRow` changes.

## Slice 6.2 — Make the real scroll container grow

**Work**

- During editing, measured content height uses `max(committedContentBottom, workspaceBottomRow)`.
- Let Compose update the real `ScrollState.maxValue` from this measured content.
- Remove synthetic edit maximums that disagree with `ScrollState`.

**Exit gate**

- There is no comment or compensation path stating that logical edit max is larger than the current real scroll max.

## Slice 6.3 — Add simple edge auto-scroll

**Work**

While MOVE is active:

```text
if pointer remains near top:
    scroll upward by a small deterministic step

if pointer remains near bottom:
    extend workspace if needed
    scroll downward by a small deterministic step

then recompute candidate from the unchanged pointer + new current scroll
```

A dwell threshold is fine. A proportional speed curve is optional and should be added only after correctness.

**Recommendation**

Delete the arbitrary “eight successful scroll changes per MOVE generation” rule. The current code documents it but does not enforce it, and it adds generation/budget machinery without a clear product benefit. Continuous edge hold should continue scrolling until the pointer leaves the edge, the gesture ends, or the logical top is reached.

**Exit gate**

- No generation budget, baseline repair, structural refreeze or live-frame mirror is needed for auto-scroll.

## Slice 6.4 — Prove the stationary-pointer invariant

**Required test**

- Start MOVE with a stationary viewport pointer.
- Scroll exactly one row stride downward.
- Recompute from current pointer/current scroll.
- Requested row must increase by exactly one.

**Exit gate**

- This test is pure and deterministic.

---

# Phase 7 — Move persistence completely outside gesture state

## Slice 7.1 — Simplify feature callbacks

**Target interface**

```text
onMoveTile(id, position)
onResizeTile(id, span)
onRemoveTile(id)
```

No request sequence IDs, move leases or acknowledgement DTOs cross the Home-grid boundary.

**Exit gate**

- Gesture/editor code can finish and clear its drag state immediately after emitting the commit intent.

## Slice 7.2 — Make the state owner responsible for reconciliation

**Likely file**

- `HomeRouteLoader.kt` or a small controller adjacent to it

**Work**

On release:

1. apply the requested placement optimistically to in-memory Home state if immediate visual response is needed;
2. call the repository;
3. on accepted mutation, use the returned normalized placement set as authoritative;
4. re-project Home state from that set;
5. on rejection/failure, restore/reload the previous persisted state.

Because row-gap normalization can move more than one tile, do not assume the requested placement alone is the final accepted layout.

**Exit gate**

- Repository acknowledgement/retry/reload behavior exists above the grid UI and nowhere in pointer logic.

## Slice 7.3 — Remove accepted-result reload redundancy where safe

If `MutationResult.Accepted` already contains the complete final normalized grid, update in-memory state directly from it instead of writing and immediately re-reading identical repository state.

**Exit gate**

- One accepted move does not require a write → full reload → UI acknowledgement lease round trip merely to stabilize the dragged tile.

## Slice 7.4 — Keep gap normalization explicitly post-commit

**Work**

- Leave `GridRowGapNormalizer` in repository mutation/load policy.
- Add tests that distinguish:
  - requested dropped position;
  - accepted normalized final positions.
- Do not call normalization during pointer updates.

**Exit gate**

- A user-visible post-drop normalization shift, if product policy requires it, is deterministic and tested rather than appearing as a movement-math bug.

---

# Phase 8 — Decompose `HomeCanvasGrid.kt`

Do this after the behavioral model is already simplified. Otherwise this phase merely spreads the old state machine across more files.

## Slice 8.1 — Extract geometry

Move only the now-stable `HomeGridMetrics` and transform helpers into `HomeGridGeometry.kt`.

**Gate:** no Compose state in the file.

## Slice 8.2 — Extract editor

Move `HomeEditState` and pure transitions into `HomeGridEditor.kt`.

**Gate:** no repository calls and minimal/no Compose dependencies.

## Slice 8.3 — Extract gestures

Move pointer routing and simple auto-scroll into `HomeGridGestures.kt`.

**Gate:** no persistence, row-window or renderer-rebasing code.

## Slice 8.4 — Extract layout

Move child measurement/placement into `HomeGridLayout.kt`.

**Gate:** it consumes placements and metrics; it does not decide legality or edit transitions.

## Slice 8.5 — Extract overlay

Move selection controls/preview visuals to `HomeGridOverlay.kt`.

**Gate:** all bounds come from metrics supplied by the caller.

## Slice 8.6 — Reduce assembly file

Rename/replace `HomeCanvasGrid.kt` with a thin `HomeGrid.kt` assembly composable.

It should mainly:

- derive metrics;
- own scroll state and edit state;
- wire gestures;
- measure render content/workspace;
- render grid and overlays;
- forward commit intents upward.

**Exit gate**

- No new god file appears.
- The function audit catches every production function and reports no Home-grid function remotely near the previous 1,882-line scale.
- Aim for roughly 100–300 lines per responsibility file, but prefer cohesion over arbitrary line targets.

---

# Phase 9 — Delete old compatibility machinery

Delete only after equivalent behavior is covered by the new invariant tests.

## Slice 9.1 — Delete obsolete MOVE frame machinery

Remove as applicable:

- `MoveCoordinateFrame` fields superseded by metrics/editor state;
- frozen scroll baseline;
- structural re-freezing;
- live move-frame mirrors;
- row-origin mapping;
- finite legality-row coupling.

## Slice 9.2 — Delete edit-frontier legality machinery

- Remove `HomeEditFrontier` rules that make frontier rows part of candidate legality.
- Keep only a tiny workspace-height helper if it still earns its own type; otherwise fold it into Home grid state.

## Slice 9.3 — Delete signed/rebased Home rendering support

After production-reference checks:

- `SignedGridPlacement` if unused elsewhere;
- signed `GridContentBounds` machinery;
- Home renderer row-origin helpers;
- Home row-window calculations;
- `WindowedSpanContainer` if no other production feature uses it.

## Slice 9.4 — Delete persistence lease machinery

Remove:

- pending move commits;
- sequence/request IDs used only to match grid acknowledgements;
- stale-ack guards in Home UI;
- move commit acknowledgement DTOs.

## Slice 9.5 — Remove forensic patch comments and dead overloads

Delete comments describing A3/P1/W3 generations, old soft-lock fixes, structural re-freeze history and compatibility overloads once their code is gone.

Keep comments that state durable rules, e.g.:

```kotlin
// Pointer Y is viewport-local; adding ScrollState.value converts it to content space.
```

**Exit gate for Phase 9**

- A repository search shows no dead alternate movement route living beside the new one.

---

# Phase 10 — Rebuild the test suite around contracts

## Slice 10.1 — Geometry tests

Required pure cases:

- cell → rect → cell round trip;
- all columns;
- representative rows;
- all supported spans;
- varying grid widths;
- varying rest offsets;
- varying scroll positions;
- center and corner grab offsets;
- leading-row preservation;
- one-row-scroll stationary-pointer invariant.

## Slice 10.2 — MOVE tests

Required:

- one cell left/right/up/down;
- diagonal;
- 1×1;
- 4×2 or largest supported tile;
- grab at several points;
- collision;
- top boundary;
- right boundary;
- invalid request retains last-valid preview;
- cancellation restores committed placement;
- **span always unchanged**.

## Slice 10.3 — RESIZE tests

Required:

- each supported span transition that should be reachable;
- collision;
- right/top constraints as applicable;
- invalid resize keeps valid preview/original according to contract;
- cancel;
- **position always unchanged**.

## Slice 10.4 — Workspace/auto-scroll tests

Required:

- workspace growth changes measured scroll extent only;
- workspace growth does not alter legality;
- downward edge scroll extends real content before scrolling into it;
- upward scroll stops at logical top;
- stationary pointer + scroll deterministically remaps candidate.

## Slice 10.5 — Repository normalization tests

Required:

- accepted move result is the authoritative final placement set;
- leading empty rows remain untouched;
- >2 empty rows between occupied groups are normalized as current policy requires;
- UI state is reconciled from the accepted normalized result.

## Slice 10.6 — Small Compose integration suite

Keep only high-value end-to-end cases:

1. horizontal tile move;
2. vertical tile move;
3. diagonal tile move;
4. collision rejection;
5. top/right out-of-bounds rejection;
6. large-tile corner grab without recentering;
7. resize using the dedicated handle;
8. downward auto-scroll while dragging;
9. gesture cancel;
10. pager does not steal active tile drag;
11. row-5 tile visibly retains leading rows.

Move implementation-detail cases out of the giant `HomeCanvasTest` and into pure unit tests.

**Exit gate**

- Most correctness coverage runs without Compose.
- Tests describe product invariants, not the deleted patch machinery.

---

# 6. Files/components to preserve, simplify or retire

## Preserve

- `GridRules.kt`: keep as the small pure legality/collision core, simplified to the new vertical contract.
- `GridRowGapNormalizer.kt`: keep as repository post-mutation policy.
- `LogicalPosition` / `LogicalSpan`: they already express nonnegative persisted Home geometry.
- square-cell sizing primitives that remain useful.
- `RenderableSpans`: keep as Home/product resize policy unless a clearer name is desired.
- existing tile rendering/content composables unrelated to geometry ownership.

## Simplify heavily

- `GridVerticalLayout` / `GridVerticalExtent`: absolute row-0 extent only, or delete if `HomeGridLayout` makes them unnecessary.
- `GridRestOrigin`: if kept, make it a presentation-offset calculation with no signed row origin.
- `MoveDragMath` / `ResizeDragMath`: preferably reduce to thin pure wrappers around `HomeGridMetrics` + grid rules, or remove them once the editor owns the operations.
- `HomeRenderProjection`: remove row-window/signed extent concerns; keep only useful projection of app data into render tiles.

## Likely retire after migration

- `SignedGridPlacement` for Home use, then globally if unused.
- signed `GridContentBounds` support if unused elsewhere.
- `GridRowWindow` for Home, then globally if unused.
- `WindowedSpanContainer` if Home was its only production consumer.
- `HomeEditFrontier` as a legality state machine.
- old mixed `HomeEditSession.Active`.
- live-session/live-frame mirrors.
- pending move lease / acknowledgement machinery.
- structural re-freeze helpers.

---

# 7. Recommended commit/slice discipline

Keep each slice independently reviewable. A good sequence is:

```text
0. docs: freeze simple Home grid contract
1. tooling: fix Kotlin function audit
2. test: add absolute-row and geometry characterization
3. grid: separate vertical legality from workspace
4. grid: switch content extent to absolute row 0
5. home: add HomeGridMetrics + tests
6. home: replace MOVE mapping, remove clamp
7. home: replace RESIZE mapping
8. home: introduce single edit-state model
9. home: replace pointer routing
10. home: replace renderer with absolute HomeGridLayout
11. home: replace edit workspace + auto-scroll
12. home: move persistence reconciliation above grid
13. home: decompose HomeCanvasGrid
14. cleanup: delete signed/windowed/refreeze/lease legacy paths
15. test: collapse patch-specific tests into invariant suite
16. docs: update final architecture and maintenance rules
```

Do not combine the first geometry rewrite, gesture rewrite, renderer rewrite and persistence rewrite into one large patch. The entire point of the plan is to make each behavior change easy to falsify.

---

# 8. Phase-wide validation gates

Every phase should stop if its preceding invariants are not green.

## Pure correctness gate

- All `core:grid` tests pass.
- All pure Home geometry/editor tests pass.
- Move always preserves span.
- Resize always preserves position.
- No invalid candidate is silently clamped.
- Leading rows remain meaningful.

## Compose gate

- Grid rect, hit-test rect and overlay rect agree for the same tile.
- Normal scrolling and edit scrolling use the actual measured content.
- No coordinate-frame animation occurs during active edit manipulation.
- Pager arbitration remains stable.

## Repository gate

- Accepted mutation results are projected directly/reliably.
- Gap normalization remains deterministic.
- Persistence failure cannot strand the UI in a pending drag/lease state.

## Complexity gate

- Fixed function-audit tooling reports the real Kotlin function bodies.
- No replacement function approaches the old god-function scale.
- No duplicate movement implementation remains behind a compatibility flag or overload.

## Full build gate

In a normal network/cached Gradle environment, run at minimum:

```bash
./gradlew :core:grid:test :feature:home:test :app:test --no-daemon
./gradlew assembleDebug --no-daemon
./gradlew lint --no-daemon
```

Run relevant instrumentation/device tests after the pure/Compose suite is green.

---

# 9. Explicit non-goals

Do **not** use this repair to launch a repo-wide abstraction cleanup.

Specifically:

- leave `FeedOverlayGatewayImpl.kt` for a separate audit;
- do not redesign unrelated launcher pages;
- do not invent virtualization for hypothetical tens of thousands of Home tiles;
- do not change tile visual design while repairing geometry;
- do not rewrite repository/database layers beyond what is needed to remove the Home acknowledgement lease and row-bound coupling;
- do not replace useful module boundaries merely to reduce type count.

---

# 10. Definition of done

The repair is complete when a developer can answer “why did this tile land on row 7?” through one short path:

```text
HomeGridGestures
  pointer viewport position
      ↓
HomeGridGeometry
  + current scroll
  - immutable grab offset
  - frozen presentation offset
  -> requested logical cell
      ↓
GridRules
  horizontal bounds + row >= 0 + no collision
      ↓
HomeGridEditor
  valid preview
      ↓
release
  commit intent
      ↓
Home state/repository owner
  persist + reconcile accepted normalized placements
```

There should be no animated logical origin, renderer row rebasing, frozen-scroll algebra, edit-legality frontier, structural re-freeze, live-frame mirror or persistence acknowledgement lease in that trace.

The best outcome is not merely a smaller `HomeCanvasGrid.kt`. It is a grid whose complete movement model is simple enough to hold in working memory, whose math has one implementation, and whose tests prove a handful of durable invariants instead of preserving years of repair scaffolding.
