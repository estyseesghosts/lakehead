# 2026-09-24 — Phase A A4 review hardening and validation follow-up
Status: completed
Owner/slice: Phase A / A4 review hardening; handoff to Phase B B1 / `:core:grid`.

## Baseline and worktree

This was a focused state-log update after independent review of the A4 work-profile launch proof. The worktree is all-untracked with no commits. Read-only `git status --short` inspection showed the all-untracked state; no staging, commit, or mutating Git operation was performed. No roadmap, task, architecture, design, or source files were changed as part of this state-log update.

## Completed and decisions

- The earlier A4 device proof was previously **1/1 on PLG110 (API 36)**. Independent review found causal/precondition and fallback gaps in the test/parser evidence. The review therefore led to test/parser hardening; it did not invalidate or overwrite the earlier device evidence.
- The current test/parser now provides ground-truth profile skip/fail behavior, managed-profile candidate selection, a prelaunch assertion that no new ActivityRecord is resumed, polling for the current personal-profile resume after an inaccessible launch call, ActivityRecord diffing, parser support for `parentId` and current-state records, and compact diagnostics.
- This records stronger test/parser checks while keeping the evidence categories distinct: there was **no post-correction PLG110 direct rerun**, because the phone had been returned. The earlier 1/1 PLG110 evidence remains historical device evidence, not a rerun result for the corrected test/parser.
- A Samsung direct rerun was also **not run**: after the prior UTP cleanup, the target and test packages were absent. This is not a new Samsung pass or skip result.
- Phase B B1 remains the only next action and remains owned by `:core:grid`; no Phase B implementation or completion is claimed here.

## Validation

After the correction, reported validation was:

- `./gradlew :app:compileDebugAndroidTestKotlin` — PASS.
- `python3 tools/scripts/function_audit.py` — PASS.
- `git diff --check` — PASS.
- `./gradlew assembleDebug` — PASS.
- `./gradlew test` — PASS; 29 unit tests were up-to-date (no test rerun in that invocation).
- `./gradlew lint` — PASS; 0 errors and 31 warnings.
- `python3 tools/scripts/repo_map.py` — PASS.
- `python3 tools/scripts/module_graph.py` — PASS.
- `python3 tools/scripts/compose_audit.py` — PASS.

Device revalidation after the test/parser correction: PLG110 direct rerun — NOT RUN (phone returned); Samsung direct rerun — NOT RUN (target/test packages absent following UTP cleanup). Do not interpret the static results as new device evidence, and do not alter the earlier A4 device record on that basis.

## Remaining and next action

One next action remains: begin Phase B B1 in `:core:grid`, implementing the coordinate, span, and collision legality slice with focused boundary, invalid-span, overlap, hole, same-item movement, replacement/rejection, and unrelated-entry preservation tests. Exit condition: B1 invariants and focused tests pass; B2 vertical extent and B3 column adaptation remain separate slices.
