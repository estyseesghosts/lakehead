# 2026-10-06 — Home grid repair Phase 0: freeze contract, fix guardrail, baseline
Status: completed
Owner/slice: orchestrator; docs contracts plus `tools/scripts/function_audit.py`. Task: `docs/agents/tasks/grid-repair-phase0.md` (repair plan Phase 0, slices 0.1–0.4).

## Baseline and worktree

- HEAD `3b1c59a86f2b2614b28e5371d36f261738dcd463`; 110 dirty/untracked paths present at start and preserved. Nothing staged, committed, stashed or reverted. Pre-existing SHA-256 recorded for every file this slice edits: `docs/architecture.md` `578ddb7e…`, `docs/design.md` `ae6ce42b…`, `tools/scripts/function_audit.py` `428cbade…`, `docs/agents/tasks/03-grid-movement-resize.md` `8a1bd39e…`, `state-logs/CURRENT.md` `c2445f98…`.
- Phase 1 (1.1–1.4) was already complete before this session; the user asked for Phase 0 knowing Phase 1 ran first. Phase 0 is documentation and tooling only, so the inversion does not invalidate Phase 1, but it did leave two contracts stale until now — see "Completed and decisions".

## Completed and decisions

- **0.1 Baseline.** Recorded HEAD plus the dirty inventory and per-file hashes above, satisfying the reconstructability gate without touching concurrent work. The uncommitted movement hardening was treated as reference material only.
- **0.2 Contract freeze.** Wrote all 15 rules of repair plan §3 into `docs/design.md` as "Frozen Home grid contract", with an explicit retired list (negative persisted Home rows, drag-time origin animation, Home row-windowing, clamped candidates, edit-legality frontiers, persistence leases) and a struck-through historical section so the old MOVE clamp/eight-step-budget wording can no longer be read as instruction. Normal-mode centering is documented as a presentation-only rest offset. `docs/architecture.md` now states the architecture consequences (one geometry owner, one route per gesture, legality in `:core:grid`, persistence above the grid) and its `:core:grid` row and rendering paragraph no longer imply Home uses row-window rebasing. `docs/agents/tasks/03-grid-movement-resize.md` is marked fully superseded with its gates/tables explicitly closed. New task spec `docs/agents/tasks/grid-repair-phase0.md` records the ordering note.
- **0.3 Guardrail fix — the substantive change.** Root cause of the miss: the old audit counted braces from the declaration line, so a parameter default like `onMoveTile: (ItemId, LogicalPosition, Long) -> Unit = { _, _, _ -> }` balanced on that line and body scanning stopped inside the signature. Rewrote `function_audit.py` as a masker plus a signature-skipping scanner: comments (including nested block comments), strings, raw strings and char literals are blanked to spaces while offsets and line numbering are preserved, the whole signature (parameter list, return type, `where` clause, generic type-parameter list) is skipped, and brace matching then measures the real body. The audit reports `HomeCanvasGrid.kt:177: 1838 lines` — the plan's "~1,882" and "lines 178–2059" were measured on a slightly different snapshot; the current body is 177–2014 and the extent was hand-verified against the file.
- Four more parser bugs surfaced during testing and were fixed before closing, each now covered by a fixture case: whitespace-only skipping after `)` read `): Int {` as an expression body; the expression-body continuation check recursed until it blew the stack; `fun <T> name(...)` type-parameter lists were skipped as nameless declarations (26 such functions exist in the tree, including `GridRules` entry points); and a `where` clause could be mistaken for the start of a body. Body-less declarations (interface/abstract members) are now skipped rather than borrowing a later function's brace.
- Regression coverage added where none existed: `tools/scripts/test_function_audit.py` (16 tests) plus a checked-in `tools/scripts/testdata/GridShapedSignature.kt` fixture shaped like `HomeCanvasGrid()` — a 20-line signature with six lambda defaults, plus separate cases for braces in comments/strings/raw strings/char literals, nested functions, generic type-parameter lists, `where` clauses, expression bodies and body-less declarations. Every expectation is an absolute fixture line number, so a fixture edit fails loudly rather than silently re-baselining. The suite includes a tripwire that `HomeCanvasGrid` is the largest function in the repository; it is documented as one that Phase 8 should delete.
- **0.4 Baseline.** `:core:grid:test`, `:feature:home:test`, `:app:test` green under `--rerun-tasks` (189 tasks executed). The first baseline attempt reported `UP-TO-DATE` on every task; that is not evidence of green tests, so the result was discarded and re-forced.
- **Scope discipline.** No Kotlin source, test, manifest or Gradle file was modified. Dependency direction is unchanged, confirmed by re-running the module graph.

## Validation

- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: PASS, 16 tests.
- Full-tree parse sweep: 2,533 function bodies measured across 251 non-build Kotlin files with zero masked brace imbalances.
- `python3 tools/scripts/function_audit.py`: PASS; 18 findings, now led by `HomeCanvasGrid.kt:177: 1838 lines`. Re-running the previous algorithm over the same tree reproduces the old 14 findings, none above 150 lines, with `HomeCanvasGrid` absent. Comparing the lists, the fix surfaced six previously invisible functions: `LauncherShell.kt:126: 313`, `AppTile.kt:258: 270`, `HomeCanvasTile.kt:82: 144`, `HomeCanvas.kt:81: 113`, `FloatingControlOverlay.kt:112: 106`, `AllAppsScreen.kt:47: 104`. It also corrected `AppTile.kt` from a mis-measured `128: 143` to the real `258: 270`, and dropped two spurious `HomeCanvasGrid.kt:2112/:2132` entries.
- Masking sweep: masked output is brace-balanced in all 251 non-build Kotlin files. Two files are unbalanced in *raw* text and correct only after masking — `app/src/androidTest/.../WorkProfileDeviceEvidence.kt` (24/25) and the new fixture (28/26) — which is direct evidence the masker is doing real work rather than passing by luck.
- `./gradlew :core:grid:test :feature:home:test :app:test --rerun-tasks --no-daemon`: PASS, 189 actionable tasks all executed, BUILD SUCCESSFUL. Two unrelated Kotlin warnings in `platform:launcherapps` and `platform:feed` are pre-existing baseline noise.
- `./gradlew assembleDebug test lint --no-daemon`: BUILD SUCCESSFUL, 591 actionable tasks (76 executed, 515 up-to-date). Run even though Phase 0 changed no Kotlin, to confirm the starting tree is green on all three and not only on the three test modules.
- `python3 tools/scripts/repo_map.py`: PASS (exit 0). `python3 tools/scripts/module_graph.py`: PASS, dependency graph identical to the Phase 1 handoff. `python3 tools/scripts/compose_audit.py`: PASS, 3 pre-existing informational findings, all comment text.
- `git diff --check`: PASS. No device run; Phase 0 changes no runtime behavior, so no device behavior is claimed.

## Remaining and next action

- Phase 2 is untouched: no `HomeGridMetrics`, no unclamped requested-cell path, no removal of `frozenScrollBaselinePx` algebra, no editor/gesture decomposition, no direct absolute-placement renderer, no scroll/auto-scroll redesign, no removal of persistence leases. The retired machinery is documented, not deleted — Phase 9 does that.
- `HomeCanvasTest.kt` (~1,961 lines) and `FeedOverlayGatewayImpl.kt` (~1,938 lines) remain large; the plan's §9 non-goals explicitly defer both.
- Worktree remains dirty with original concurrent/user work plus this slice. HEAD unchanged; nothing staged or committed.
- **One next action:** when authorized, implement only repair Slice 2.1 in `:feature:home` — the pure immutable `HomeGridMetrics` with forward-rect and inverse-raw-cell tests. Exit when one tested object provides both transforms, with no gesture or render caller migrated yet.

## Touched paths for this objective

- `docs/design.md`, `docs/architecture.md`, `docs/lakehead-grid-repair-plan.md` (Phase 0 status line only), `docs/agents/tasks/03-grid-movement-resize.md`.
- `docs/agents/tasks/grid-repair-phase0.md` (new).
- `tools/scripts/function_audit.py` (rewritten), `tools/scripts/test_function_audit.py` (new), `tools/scripts/testdata/GridShapedSignature.kt` (new fixture).
- `state-logs/CURRENT.md`, this handoff.