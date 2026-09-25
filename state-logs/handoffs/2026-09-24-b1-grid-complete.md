# 2026-09-24 — B1 grid coordinates, spans, and collision legality
Status: completed
Owner/slice: `:core:grid` / [B1 task specification](../../docs/agents/tasks/b1-grid-coordinates.md)

## Baseline and worktree

B1 implementation and validation were completed before this focused durable-state update. The repository worktree is all-untracked with no commits. Read-only Git status inspection confirmed the all-untracked state; no staging, commit, or mutating Git operation was performed. This update changes only roadmap/task/state documentation; it does not change source. The implementation state and validation outcomes below are recorded as supplied for this completed slice.

## Completed and decisions

- B1 is complete in the `:core:grid` owner. The pure model consists of zero-based non-negative `GridPosition`, positive `GridSpan`, and immutable generic `GridPlacement<T>`.
- The exact supported span set is 1×1, 2×1, 1×2, 2×2, and 4×2. Other positive spans are representable but unsupported by legality; non-positive dimensions are invalid.
- `GridRules` implements place, move, and resize with explicit finite positive grid dimensions. Explicit replacement is opt-in by item ID and replaces at its existing list position.
- Rectangular occupied-cell overlap is rejected; edge-touch is legal. Bounds are checked, and move/resize ignore the target item's old footprint.
- Rejections are distinct: unsupported span, out of bounds, collision, and unknown item. Duplicate placement IDs without explicit replacement are rejected.
- Successful operations preserve unrelated entries and their order; ordinary placement preserves holes. Operations do not mutate input collections.
- B1 acceptance passed. B2 vertical extent and B3 column adaptation remain separate and are not claimed complete.
- Durable contract/evidence is captured in `docs/agents/tasks/b1-grid-coordinates.md`; roadmap status and immediate-next wording now point to B2.

## Validation

- 20 focused `GridRulesTest` tests — PASS.
- `:core:grid` lint — PASS.
- `python3 tools/scripts/function_audit.py` — PASS.
- `./gradlew assembleDebug` — PASS.
- `./gradlew test` — PASS; 49 XML unit tests total (29 prior plus 20 B1). Gradle reported the test work up-to-date.
- `./gradlew lint` — PASS; 0 errors and 31 warnings.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS.
- `python3 tools/scripts/function_audit.py` — PASS.
- `python3 tools/scripts/compose_audit.py` — PASS.
- Gradle reported NO-SOURCE for app, feature, designsystem, and fixtures unit-test tasks.
- Device verification — NOT RUN; B1 is a pure grid-logic slice and no B2/B3 code or device work was performed.
- Automated validation of this documentation-only state update — NOT RUN.

## Remaining and next action

No B1 acceptance item remains open. One next action: implement B2 vertical extent in `:core:grid`, deriving the canvas extent from occupied cells plus the required working margin/viewport and covering distant rows, sparse gaps, no arbitrary maximum row, deterministic bottom boundary, repeatability, and preservation of unrelated placements. Exit condition: B2's focused extent tests pass with its defined extent contract; do not include B3 column adaptation.
