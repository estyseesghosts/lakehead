# 2026-09-26 — Colour milestone: B2 scale-up + all prior slices complete
Status: completed
Owner/slice: Phase A (A1–A3) + Phase B (B1–B3) of `docs/agents/tasks/colour.md`

## Baseline and worktree
Clean worktree at start of colour milestone work. Prior state: launcher had no Material theming, 6-column reference, 16dp edge padding, 8dp gap, 48dp minimumCell, dark hardcoded tile colours, no centred grid origin, no nav pill theming. All colour work built on existing 0B foundation (D5 resize editing etc. already committed).

## Completed and decisions
Eight commits landed in sequence via dedicated coding agents, each independently verified:

| Commit | Slice | Summary |
|--------|-------|---------|
| `8e090a4` | A1 | LauncherTheme with dynamic (API 31+) + static fallback palettes; `MaterialTheme(colorScheme=...)` colour-only, no stock components |
| `342f217` | A2 | TileColorRole enum (Normal/Accent); semantic colour resolution from scheme; deleted all hardcoded `0xFF…` tile constants |
| `3ef5fe8` | A3 | Theme wiring in MainActivity/LauncherShell; background from scheme; status/nav scrim transparent |
| `cdead40` | — | Nav pill theming (ad hoc, not a numbered slice) |
| `9c6293a` | B3 | 16dp→8dp horizontalPaddingPx; centred originX through WindowedSpanContainer/TileBounds/overlays/drag math; spec update noting dual-change discovery |
| `08ef031` | B1 | LauncherDimensions data class + LocalLauncherDimensions composition local; gap 8dp, inset 8dp, tilePadding 8dp, symbolSize tokens wired through AppTile |
| `7a37ada` | B2 | Reference column count 6→5; columnsBound 6→5; AddToHomeCoordinator updated; grid unit tests updated |
| `34e6f1f` | B2 scale-up | minimumCellPx 48→60dp; vertical + horizontal pin tests for new cell size; 221 unit tests pass |

**Key decisions:**
- B3 discovery: both padding change AND originX fix were required together. Prior assumption (padding-only) would have left grid flush-left. `SquareCellGeometry.fromViewport` subtracts padding for cell size but didn't pass origin; `HomeCanvasGrid`/`WindowedSpanContainer` needed explicit originX threading.
- B2 scale-up: minimumCell 48→60dp needed to keep tiles comfortably tappable at 5-column reference; pin tests added to prevent regression.
- LauncherDimensions tokens replace raw `8.dp` literals in AppTile; composition local pattern chosen over DI for composable-scoped access.
- All tile colour constants deleted from AppTile.kt; scheme-derived resolution with Normal/Accent distinction and comment citing grid.md §12.

## Validation
All slices validated by implementing agents:
- `./gradlew assembleDebug` — SUCCESS (all slices)
- `./gradlew test` — SUCCESS (221 tasks verified on B2 scale-up commit)
- `./gradlew lint` — SUCCESS (all slices)
- Reviews by three agents: all PASS. B3 had advisories (fixed). B2 noted stale doc references (non-blocking).

**Device rendering verified by user:**
- 1080px @ 3× → 5 columns, cell 187px, originX centred, edge = gap = 8dp
- LauncherTheme light/dark switching works
- Nav pill is theme-adaptive
- Device colours confirmed good by user

**Not run:** `repo_map.py`, `module_graph.py`, `function_audit.py`, `compose_audit.py` (not explicitly required per-slice; should run at phase gate).

## Remaining and next action
**Known issues / deferred:**
- App-list (AllAppsScreen) font still pure black, unreadable on dark theme — deferred to E1 row geometry work.
- All other Phase C–G slices not yet started.

**Unverified device behaviour:**
- No instrumentation-level verification (HOME selection, profile launch, package change callbacks) during colour phase; visual-only device checks.

**Worktree/commit state:**
- Branch clean at `34e6f1f` (last commit: B2 scale-up).
- `docs/first_draft.md` modified unstaged (unrelated). `docs/shelter.md` + `docs/shizuku.md` untracked (unrelated, leave).
- No push performed.

**One concrete next action:**
Implement **C1 — Square / rectangular tiles** per `docs/agents/tasks/colour.md` §3 Phase C.

- **What:** Change `TileShape` in `core/designsystem/.../AppTile.kt` from `RoundedCornerShape(12.dp)` to `RectangleShape`. Keep a single `TileShape` constant so `ResizeCandidateOverlay.kt` and `HomeCanvasGrid.kt` MoveCandidateOverlay pick it up. If any call site hardcodes 12dp for corners, redirect to the shared constant.
- **Acceptance:** `TileShape == RectangleShape` asserted by unit/semantics test; device: home tiles have sharp corners at all spans (1×1, 2×1, 1×2, 2×2, 4×2); `assembleDebug` + `test` + `lint` clean.
- **Exit condition:** C1 committed; device shows square corners on all tile spans; validation commands green.