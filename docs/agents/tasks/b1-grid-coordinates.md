# B1 — Grid coordinates, spans, and collision legality

Status: complete  
Owner: `:core:grid`  
Phase: B / 1A grid engine

## Objective and boundary

Provide a pure, framework-free model for non-negative grid coordinates, positive rectangular spans, generic item placements, and place/move/resize legality on an explicitly sized finite grid. B1 does not calculate unbounded vertical extent or adapt the logical grid to viewport width; those are separate B2 and B3 slices.

## Contract

- `GridPosition` represents a zero-based `(column, row)` coordinate. Both values are non-negative; invalid construction fails.
- `GridSpan` represents positive rectangular cell dimensions. Its exact supported set is 1×1, 2×1, 1×2, 2×2, and 4×2. Other positive dimensions can be represented but are rejected for placement legality; zero or negative dimensions fail construction.
- `GridPlacement<T>` is a generic immutable value associating an item ID, position, and span.
- `GridRules` exposes `place`, `move`, and `resize` against explicit positive `columns` and `rows` bounds.
- Replacement is explicit through `replacingId`; it replaces at the original list index and still validates against all other placements. Duplicate IDs without explicit replacement and unknown replacement IDs reject.
- Rectangular occupied-cell overlap is a collision. Placements that only touch at an edge are legal.
- Legality checks include supported-span validation, grid bounds, rectangular collision checks, and ignoring the existing item's own occupied cells while moving or resizing it.
- Results distinguish `UNSUPPORTED_SPAN`, `OUT_OF_BOUNDS`, `COLLISION`, and `UNKNOWN_ITEM` rejections.
- Operations return accepted placement lists without mutating caller input. Existing unrelated placements and ordering are retained; ordinary placement can fill a hole without compacting other entries.

## Acceptance criteria — satisfied

- Coordinates and dimensions enforce their constructor invariants.
- Only the exact B1 span set is supported by legality rules.
- Placement, movement, resizing, and explicit replacement apply bounds and collision checks.
- Edge-touching rectangles are accepted; actual rectangular overlap is rejected.
- Self movement/resizing ignores only that item's old footprint.
- Rejection reasons remain distinct, including unsupported span versus out-of-bounds and unknown item.
- Accepted operations preserve unrelated entries and their order; rejected and accepted operations do not mutate input collections.
- Focused `GridRulesTest` coverage passes, including edges, holes, collisions, same-item move/resize, replacement and rejection behavior.

## Evidence

- 20 focused `GridRulesTest` tests pass.
- `:core:grid` lint passes.
- `python3 tools/scripts/function_audit.py` passes.
- Root validation after B1: `./gradlew assembleDebug` passes; `./gradlew test` passes with 49 XML unit tests (29 prior plus 20 B1; Gradle reported the test work up-to-date); `./gradlew lint` passes with 0 errors and 31 warnings.
- All four repository audits pass: `python3 tools/scripts/repo_map.py`, `python3 tools/scripts/module_graph.py`, `python3 tools/scripts/function_audit.py`, and `python3 tools/scripts/compose_audit.py`.
- Gradle reported NO-SOURCE for app, feature, designsystem, and fixtures unit-test tasks.
- No device behavior was required or tested for this pure grid slice.

## Explicitly not included

- B2 vertical extent / working margin or viewport calculations.
- B3 column-count adaptation.
- Rendering, persistence, Android, or Compose integration.
