# B4 — Generic span legality (grid.md follow-up to B1)

Status: open
Phase: B / 1A grid engine (follow-up)
Owner: `:core:grid` (mechanical test updates in `:data:repository` only where they assert the removed rejection)

## Objective and boundary

Generalize `GridRules` place/move/resize legality from the phase-limited B1 allowlist to the `docs/grid.md` open span set: any positive integer span is legal when it satisfies bounds + rectangle collision. This is pure framework-free grid logic. It changes no persistence schema, no repository contract shape, no UI, and no packing behavior.

Background: `GridSpan` representation is already generic positive integers, but `GridRules.legality` rejects anything outside 1×1, 2×1, 1×2, 2×2, 4×2 with `UNSUPPORTED_SPAN` (`core/grid/.../GridRules.kt`, `GridSpan.kt`). `docs/grid.md` adds 3×2, 4×3, and 3×3 as valid examples of the open set and forbids `SMALL`/`MEDIUM`/`LARGE`-style enums. D1/D2 persistence already stores generic dimensions and needs no migration.

## Contract changes

- Delete the supported-span gate from `GridRules.legality`: remove the `isSupported` check, the `SUPPORTED` set, and `isSupported` as a legality source. `GridSpan` remains a plain positive-dimensions value (constructor still rejects zero/negative).
- Remove `Rejection.UNSUPPORTED_SPAN` from the `GridRules.Rejection` enum. Remaining reasons stay distinct: `OUT_OF_BOUNDS`, `COLLISION`, `UNKNOWN_ITEM`.
- Legality for every op is exactly: finite positive grid bounds, candidate within bounds (`column + span <= columns`, `row + span <= rows`, `Long`-safe as today), rectangular overlap is a collision, edge-touching is legal, and the item's own footprint is ignored for move/resize. Replacement (`replacingId`) keeps its current index/validation semantics.
- Accepted ops return fresh lists preserving unrelated entries, order, and holes; rejected ops mutate nothing. No auto-compaction, sorting, or packing is added.
- `:data:repository` needs no behavior change: it already passes the grid result through unchanged (`HomePlacementRepository.kt`). Only repository/grid tests asserting `UNSUPPORTED_SPAN` are updated mechanically to the new reasons.

## Acceptance criteria

- 3×2, 3×3, and 4×3 spans place/move/resize successfully when in-bounds and collision-free.
- Oversized or out-of-bounds generic spans reject with `OUT_OF_BOUNDS`, never a span-support reason.
- Overlapping generic spans reject with `COLLISION`; edge-touching rectangles accept.
- Move/resize of an item ignore only that item's old footprint.
- Unknown item / wrong replacement target still reject with `UNKNOWN_ITEM` (and duplicate-ID semantics unchanged).
- No `UNSUPPORTED_SPAN` symbol remains in source or tests; no `SUPPORTED`/`isSupported` legality gate remains.
- Persistence, repository operation shapes, and UI behavior are unchanged by this slice.

## Required tests (`:core:grid`, plus mechanical updates in `:data:repository`)

- Place/move/resize accepted cases for 3×2, 3×3, 4×3 (free space, in-bounds).
- `OUT_OF_BOUNDS` for a generic span exceeding bounds (e.g. 3×2 at the right edge, distant-row overflow safety preserved).
- `COLLISION` vs edge-touching for generic spans.
- Self move/resize ignoring own footprint with a generic span.
- Replacement with a generic span at the original index; unknown replacement still `UNKNOWN_ITEM`.
- Snapshot-before/after proof of zero writes on rejection at the grid-result level (repository tests keep their existing snapshot style; only the expected reason changes).
- Existing B1/B2/B3 tests keep passing unmodified except assertions on the removed rejection.

## Explicitly deferred

- Renderable-size policy (which spans the UI offers in D5/F3) — a UI/editing concern, not a grid rejection.
- D4 move gestures, D5 resize UI, tile appearance/shortcut slots, hit-target separation, symbol pipeline — later slices.
- Filtered/packed projections — unchanged; this slice adds no packing.
- Schema or repository contract changes — none.

## Validation

```text
./gradlew :core:grid:test
./gradlew :data:repository:testDebugUnitTest
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Record exact outcomes; `NO-SOURCE` is distinct from pass. Confirm the module graph is unchanged (`:data:repository → :core:grid` delegation only) and no Room/Android/Compose types enter the grid contract.

## Narrow exit gate

B4 is complete when generic positive spans are governed only by bounds + collision, `UNSUPPORTED_SPAN`/`SUPPORTED` are gone, the required tests plus full validation pass, and no D2 contract, schema, UI, or packing behavior is claimed.
