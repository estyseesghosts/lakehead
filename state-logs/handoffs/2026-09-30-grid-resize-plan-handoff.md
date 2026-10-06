---
# HANDOFF PROMPT — 2026-09-30 grid movement/resize plan

You are the next agent. Start by reading `AGENTS.md`, `state-logs/CURRENT.md`,
`docs/agents/tasks/03-grid-movement-resize.md`, the B1/B2 sections of
`docs/agents/tasks/02.md`, and these prior handoffs:

- `state-logs/handoffs/2026-09-30-move-velocity-guard.md`
- `state-logs/handoffs/2026-09-29-nav-viewport-guard.md`

Then inspect `git status --short` and `git log --oneline -15`. Never use `git add .`,
and never overwrite concurrent work. This is a bounded planning/implementation
handoff: primary owner is `:feature:home`; keep `:core:grid` and
`:core:designsystem` unchanged unless the settled plan explicitly requires an
owned contract change.

## Active gate: Phase 1 contracts

Do **not** start implementation until these decisions are explicit, reviewed,
and unambiguous:

1. **D4 OOB versus clamp:** define exactly when the span-aware mapper clamps to
   a fitting location and when it surfaces out-of-bounds because the span cannot
   fit at all.
2. **Structural re-freeze boundary:** enumerate frame inputs that are structural,
   define the predicate, and specify how an atomic re-freeze preserves the
   active pointer/grab relationship. Ordinary live-scroll changes use frozen
   baseline plus live delta and do not re-freeze.
3. **Eight-step budget semantics:** the budget is eight successful one-row
   `ScrollState` changes per MOVE drag, in either direction and across exits and
   re-entry; no-op attempts are free; reaching the cap stops the ticker while
   keeping the drag active; lift/cancel ends the drag and stale generations do
   no work.
4. **Resize transfer contract:** tap-to-arm remains separate from slop-time
   routing; transferred selection preserves armed/unarmed state as specified;
   release preserves the editing arm state. Confirm exact armed-handle,
   unarmed-handle, and body routes before editing shared gesture code.

 Record the decisions in the appropriate stable contract documentation when the
 plan calls for it; do not treat an implementation as proof that a contract was
 settled.

## Required execution order

Follow the narrow request sequence exactly:

`explore (zero) → plan (three) → implement (five/four/six) → review (three) → commit (one)`

Keep requests narrow and serialized around shared `HomeCanvasGrid` gesture
paths. Implement the plan phases in order: Phase 1 contracts, Phase 2 M1 stable
MOVE coordinate frame, Phase 3 M2 bounded autoscroll, Phase 4 R1 slop-time
resize routing, Phase 5 R2 transfer/release routing, then Phase 6 regression
matrix/device validation. Do not broaden into persistence, platform catalogue,
folders, widgets, search, notifications, or a second grid/packing system.

The resize acceptance conditions from B1/B2 are mandatory: the toggle is the
drag handle; the first armed drag resizes; dragging never disables resize mode;
release leaves mode active under the agreed editing rules; larger spans are
testable. Preserve fixed-span body MOVE behavior, collision/legality ownership,
and tap/long-press/page-swipe behavior unless the settled contract identifies a
specific defect.

## Validation and stop condition

Every programming slice must run and record exact outcomes for:

- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew lint`
- relevant module tests (including the named `:feature:home` tests)
- `:app:installDebug` on **SM-G986W**

Distinguish real PASS from `NO-SOURCE`; record any **No-hierarchies blocker**.
An installed APK proves no gesture behavior, so make no gesture claims from
install alone. Phase 6 must include device evidence for MOVE/autoscroll/resize
pointer behavior and larger spans, with device/OS and observed outcomes.

Before ending, update this handoff with factual paths changed, settled
decisions and reasons, exact validation, failures/limitations, unverified
device behavior, and worktree/commit state. Leave one concrete next action and
an exit gate. The installed APK must remain on SM-G986W, then **STOP and wait
for explicit user device verification**. It is explicitly forbidden to continue
working until the user confirms the fix is working; if verification fails, file
a bounded follow-up rather than silently proceeding.

Status: planning gate open; no implementation performed by this handoff.
Owner/slice: orchestrator / next agent, `:feature:home`, task
`docs/agents/tasks/03-grid-movement-resize.md`.
