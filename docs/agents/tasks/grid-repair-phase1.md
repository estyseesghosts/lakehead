# Home grid repair Phase 1 — absolute row semantics

Authority: `../../lakehead-grid-repair-plan.md`, slices 1.1–1.4. This is not the historical movement/resize plan's Phase 1.

Status: **completed and reviewed (2026-10-05)**. All four gates pass; full build/tests/lint and sparse-row device evidence are recorded in `../../../state-logs/handoffs/2026-10-05-grid-repair-phase1.md`. Later repair phases are not implied complete.

## Ownership and scope

`:core:grid` owns legality and extent. The repository owns persisted placement and post-commit/load row-gap normalization; app wiring supplies columns, not a finite Home row bound. `:feature:home` owns presentation and consumes absolute nonnegative placements. No new module or dependency direction is needed.

## Serialized acceptance

1. Add pure tests before production edits: row zero is permanent; a row-5 1×1 tile extends content through row 6, a 1×2 through row 7; extent is maximum row plus span; leading rows survive normalization; negative persisted positions are rejected.
2. Remove public finite vertical placement bounds in core/repository and update callers. Logical legality requires nonnegative row/column, positive span, horizontal fit and no logical overlap. UI workspace changes cannot alter the same candidate's validity; remove vertical frontier clamping/rejection where necessary to meet this gate, without performing the later geometry rewrite.
3. Measure nonnegative Home content from row zero, never occupied max minus min. Preserve independent signed shared contracts if still used.
4. Home constructs no `SignedGridPlacement` and derives no logical/renderer row origin from occupied minimum. Keep shared primitives with remaining callers; keep row-window rendering migration for Phase 5.

## Verification and exclusions

Run relevant core/model/grid, repository, Home and app tests; `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`, and repo-map/module-graph/function/Compose audits for each implementation slice. Distinguish intentional pre-production red tests, unrelated baseline failures, NO-SOURCE and device evidence. Inspect dependency/caller references and diff; preserve all starting dirty work. Do not stage or commit.

No new geometry implementation, editor state redesign, gesture routing rewrite, direct-renderer replacement, auto-scroll rewrite or persistence acknowledgement redesign. Device behavior is unverified until actually exercised. Execution status and exact results live in `../../../state-logs/CURRENT.md` and its linked handoff.
