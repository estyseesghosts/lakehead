# 2026-10-07 — Docs-vs-code staleness audit (read-only)

Owner/slice: orchestrator. Constraint: do not touch design docs, do not edit code.
Method: orchestrator read of `architecture.md`, `design.md`, `platform-capabilities.md`,
`LauncherDestination.kt`, `MainActivity.kt`, manifest, `GridVerticalExtent.kt`;
`zero` subagent audit of grid-plan docs; `zero` subagent audit of peripheral docs.
HEAD `c2613b7` + dirty Phase 10 worktree.

## Highest-risk stale guidance (fix first when docs are next opened)

1. **Five-vs-six column contradiction is unresolved across live docs.**
   `AGENTS.md:44` mandates six 1×1 cells across; `docs/design.md:39`,
   `docs/architecture.md:36`, `docs/grid.md:12,850` say five; code reference is
   five (`GridColumns.kt:11 NORMAL_PHONE_REFERENCE_COUNT = 5`,
   `MainActivity.kt:154,167,175` `columnsBound = 5` floor) while device-adapted
   value on SM-G986W is six (all `HomeCanvasTest` stride math uses width/6 and
   passes on device). `docs/archive/00.md:22-24` once ruled FIVE authoritative;
   `AGENTS.md:44` now says six. Owner decision needed: floor/reference (5) vs
   adapted phone value (6). `launcher_report.md:239` ("exactly 6") and
   `startscreen.md:419` (six-unit) take the six side; `grid.md`/code take five.
2. **Broken roadmap pointer:** `docs/first_draft.md` does not exist (content lives at
   `docs/archive/first_draft.md`) but is cited as the roadmap in `AGENTS.md:5,21`,
   `docs/architecture.md:50`, `docs/design.md:37`, `README.md:17`, `docs/roadmap.md:63`.
3. **`docs/navbar.md` contradicts code** (four tabs vs three in
   `LauncherNavPill.kt:15-17` / `LauncherShell.kt:66`; component tree
   `FloatingTabBar/...` vs `NavigationControlPill/Button`; parent-owned selection
   circle vs per-button) yet `docs/roadmap.md:62` lists it as a stable contract.
4. **`docs/roadmap.md:363`** claims `GridRules` rejects outside-B1 spans with
   `UNSUPPORTED_SPAN` — symbol does not exist in any `.kt`; legality is generic
   bounds + collision. Same staleness in `docs/agents/tasks/b4-generic-span-legality.md:3`
   ("Status: open") and `b1-grid-coordinates.md:52`.
5. **Shelter/Shizuku module names cited as live contracts** in
   `docs/architecture.md:46` ("Full contracts live in `docs/shizuku.md` and
   `docs/shelter.md`"): `shelter.md:426-447` proposes `launcher-appmodel/` etc.,
   none exist; zero Shizuku implementation/dependency. Ownership principles match
   code direction; module map does not.

## Stale status / pending-tone wording in live docs

- `docs/platform-capabilities.md:15` ("cannot yet be selected as HOME"): manifest
  declares HOME filter, `MainActivity.kt:341-343` requests `ROLE_HOME`, slice 0B
  Complete. `:8` "planned `:platform:shortcuts`": module exists with real code.
- `docs/architecture.md:40` "planned" list includes now-real modules
  (`core:navigation`, `data:database`, `data:repository`, `platform:shortcuts`,
  `feature:allapps`); `:15` Phase 9 "deletion list" reads pending but Phase 9 is
  complete (only the behavior-free `SignedPixelFrame` type name survives);
  `:24`/`:36` "row window remains for shared callers" — no window machinery
  remains anywhere; `:32` invokes `FreePlacementEngine`, which exists only in docs
  (real concept: `GridFirstFreeCell`).
- `docs/design.md:27` same pending Phase 9 deletion-list tone; `:52` "Slice 0A only
  displays a placeholder" — `HomePlaceholder` has no production caller.
- `docs/agents/tasks/grid-repair-phase10.md` (untracked scope doc, "not started"):
  every "Known … not yet changed" section (10.1a/10.1b/10.2a/10.4a) and counts
  47/82/537 are stale vs the dirty worktree (10.1a/10.1b/10.2a/10.4a executed;
  home unit 531, device 49/49). `grid-repair-phase9.md` symbol claims confirmed,
  but its "kept" list and 537 count were superseded by Phase 10.
- `docs/roadmap.md:1278-1287` next-slice ordering + `:3` "TBD" stale (Phase 10
  complete); `:34` "29 unit tests" stale (130/155/531/108).
- Task specs with stale "open/in-progress" status vs implemented code:
  `b4-generic-span-legality`, `d4-move-editing`, `d3-viewport-aware-home-rendering`
  (evidence-complete handoff exists), `grid-repair-phase8.md:61` "Phase 9 not started".
- `lakehead-grid-repair-plan.md` §1/§2 measurements (3,166-line HomeCanvasGrid etc.)
  are pre-repair baseline (file now 674 lines); §3 contract + §4 ownership confirmed
  in code except the `HomeGrid.kt` filename (superseded by recorded Phase 8 decision
  to keep `HomeCanvasGrid`).

## Historical / superseded (harmless unless cited as current)

- `docs/01.md` ("feed/shortcuts not functional", move/resize coupling) — all
  implemented. `docs/02.md` — work done. `docs/startscreen.md` + identical
  duplicate `docs/Oneshot/00/gridlayout.md` — negative-row/six-unit claims
  contradicted (bias-0.45 rest origin absorbed into `GridRestOrigin.kt`).
- `docs/adaptive_home_screen_tile_layout.md` — layout rules superseded by `grid.md`
  C1 amendment + `ShortcutStripGeometry`; notification-per-tile unimplemented
  (no `:platform:notifications`). `docs/Oneshot/00/shortcuts.md:200-217`
  swipe-to-open contradicts its deliberate removal. `docs/Oneshot/00/pages.md`
  feed-"not implemented" note stale; rest current. `wigglemode.md`, `00_fixplan.md`
  (also stale `docs/00_fixing.md` path), `archive/00.md`, `archive/00_fixing.md` —
  history, no live citations except via stale paths.
- `docs/grid.md` CURRENT overall; stale acceptance items: boat-wheel placeholders
  (`:440-442,865`) — placeholders gone. `docs/immersive.md` CURRENT (implemented
  verbatim). `docs/launcher_report.md` target-spec, but "exactly 6" (`:239`) and
  "four buttons" (`:114-119`) contradict code + grid.md; single-home reality vs
  Home 1+2 (`:25-44`); schema lists folders/widgets/notification tables that do
  not exist.

## Code-KDoc (reported, not edited)

- `GridVerticalExtent.kt:8-9` addresses "shared signed placement callers" — none remain.

## Not re-verified

`git log` was permission-denied for subagents (commit-state claims inferred from
handoffs + `git status`); device counts cross-checked against state-logs only;
no builds or device runs in this audit.
