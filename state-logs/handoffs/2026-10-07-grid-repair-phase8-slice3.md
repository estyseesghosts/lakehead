# Handoff — Home grid repair Phase 8, Slice 8.3 (2026-10-07)

## Completed (factual)
Slice 8.3 only. Slices 8.4–8.6 not started.
- Found `HomeGridGestures.kt` already held the pointer loop, `resolveDragEnd` and `homePagerGesture`; the ticker already lives in `HomeEdgeAutoScroll.kt` (`runMoveEdgeAutoScroll`).
- Moved verbatim from `HomeCanvasGrid.kt` to the end of `HomeGridGestures.kt`: `DownDragRoute`, `routeDownDrag`, `transferDragRoute`, `PointerEndDecision`, `classifyPointerEnd`.
- `HomeCanvasGrid.kt` 921 → 845 lines; `HomeCanvasGrid()` 621 lines (unchanged).

## Touched paths
`feature/home/.../HomeCanvasGrid.kt`, `feature/home/.../HomeGridGestures.kt`, `docs/agents/tasks/grid-repair-phase8.md`, `state-logs/CURRENT.md`, this handoff. No test files changed or moved (tests reference the symbols in the same package; counts preserved).

## Decisions
- `moveEdgeXPx`/`moveEdgeYPx` stay in `HomeCanvasGrid`: one owner (the composable's snapshot state), written by the `HomeDragHost` callbacks and cleared by back/exit/hide paths wired there, read by the ticker lambda. Moving into a holder would relocate, not clarify, and 8.6 owns assembly state.
- `resizePreviewSpan` stays: it is render preview, belongs with 8.5 overlay.
- Tripwire test not deleted, threshold untouched.
- Process note: one move step used `sh -c "printf … && cat …"` (a chained command, contrary to AGENTS.md §6); no effect on results.

## Validation (each run separately, 2026-10-07)
- `./gradlew :feature:home:compileDebugKotlin --console=plain`: PASS.
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks --console=plain`: PASS; XML summed `:core:grid` 184, `:feature:home` 547, `:app` 108, 0 failures.
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS.
- `:feature:home:connectedDebugAndroidTest` on SM-G986W (R5CN70E2SGV): 82 tests, 0 failures.
- `:app:connectedDebugAndroidTest` (two shell classes excluded): 33 tests, exactly the 4 known failures (`MainActivitySeededHomeDeviceTest` ×3 30 s timeouts; `WorkProfileLaunchProofTest` work profile 150 missing).
- `module_graph.py`: no new edges. `function_audit.py`: `HomeCanvasGrid` 621 lines. `compose_audit.py`: no new flags (pre-existing comment/test hits only). `repo_map.py`: ran.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 16 tests, 1 failure, the accepted tripwire.
- `git diff --check`: clean.

## NOT run / not verified
Real-finger drag, real-finger edge hold / auto-scroll (only instrumentation-driven coverage), drop followed by normalization shift on device, possible one-frame flash from loader `StateFlow` lag.

## Worktree
Phase 3.2–8.3 work was uncommitted on HEAD `cb3d38f`. The user then explicitly asked for a commit of all changes and `installDebug` on the SM-G986W; see the final report for the resulting commit hash and install result.

## Next action
Slice 8.4: move child measurement/placement into `HomeGridLayout.kt` (already exists from earlier phase; review what remains in `HomeCanvasGrid.kt`). Exit: layout consumes placements and metrics, decides no legality or edit transitions; unit counts 184/547/108, compile/tests/lint green, device 82/0 and 33/4-known.
