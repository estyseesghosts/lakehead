# Home grid repair Phase 0 — freeze contract, fix guardrail, baseline

Authority: `../../lakehead-grid-repair-plan.md` Phase 0 (slices 0.1–0.4). Phase 0 changes no grid behavior; it makes the later migration safe and verifiable.

Status: **completed and reviewed (2026-10-06)**. Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase0.md`.

## Ordering note

Phase 1 (slices 1.1–1.4) was implemented before Phase 0, by user direction. That ordering is acceptable — Phase 1 established row semantics, which Phase 0 only documents — but it left two documented contracts stale until this phase: the architecture/design docs still described signed/rebased/windowed Home movement as intended, and the function audit could not see the function the repair exists to fix. Both are corrected here.

## Ownership and scope

Docs-only for the contract, plus one repository-owned tool script. No Gradle module, Kotlin source, test or manifest change is in scope, so dependency direction is untouched.

## Serialized acceptance

1. **0.1 Snapshot.** Record the commit and dirty-file inventory before migration, and preserve the pre-existing uncommitted movement hardening as reference material rather than architecture to keep.
2. **0.2 Supersede.** Write repair plan §3 into the repository as the frozen Home grid contract; mark negative persisted Home rows, drag-time origin animation, Home row-windowing, clamped candidates, edit-legality frontiers and persistence leases as retired; preserve normal-mode centering as a visual option, never a logical origin; leave no active task document instructing further structural re-freezing, signed Home rows, row-window movement frames or eight-step MOVE generations.
3. **0.3 Guardrail.** Make `function_audit.py` skip the whole signature before counting body braces, ignore braces in comments/strings/raw strings/char literals, and carry a regression fixture shaped like `HomeCanvasGrid()`. The audit must report the real ~1,838-line `HomeCanvasGrid()` before decomposition.
4. **0.4 Baseline.** Run the relevant suite from a forced re-execution, not an up-to-date cache, before any Phase 2+ refactor. Record red tests explicitly rather than carrying an unknown baseline.

## Verification and exclusions

Run `python3 -m unittest discover -s tools/scripts -t tools/scripts` for the guardrail. Distinguish forced execution from `UP-TO-DATE`, and NO-SOURCE tasks from real test results. Full `./gradlew assembleDebug`, `test`, `lint` and the four audits apply to implementation slices; Phase 0 changed no Kotlin, so they are not re-run here.

No Phase 1+ production change, no geometry/editor/gesture/persistence work, no device claims, and no staging or commit. Do not "fix" the retired machinery while documenting it — Phase 9 deletes it.

Execution status and exact results live in `../../../state-logs/CURRENT.md` and its linked handoff.