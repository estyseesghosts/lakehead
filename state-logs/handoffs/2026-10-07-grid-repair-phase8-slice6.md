# Handoff — Home grid repair Phase 8, Slice 8.6 and exit gate (2026-10-07)

## Completed (factual)
- Audit: `HomeCanvasGrid()` was already the assembly (scroll/edit/pager state, hold and selection emission, modifier wiring, forwarding commit intents). It holds no editor transition logic, routing predicate or overlay rendering; the remaining one-line call-site transitions sit with the state owner.
- Moved `moveEdgeXPx`/`moveEdgeYPx` into new `MoveEdgePointer.kt` (`MoveEdgePointer`: `set`, `clear`, `position`). One owner of the held MOVE pointer; written by the drag host, read by the ticker, cleared on every exit path (six paired clears became `clear()`). Done by a `four` subagent; I reviewed the full file and every call site.
- Moved the `minHeightPx` derivation to pure `moveWorkspaceFloorPx(...)` in `HomeEditWorkspace.kt` (done by me, verbatim logic). `workspaceBottomRow` state stays in the composable.
- `renderedSpanOf` stays: one-line `editState.renderedSpan` read passed to `HomeGridLayout`.
- Did not rename to `HomeGrid.kt`: the composable/file keep the name `HomeCanvasGrid` (callers in `HomeCanvas.kt`, KDocs and the audit tests reference it; a rename changes no responsibility).
- `HomeCanvasGrid.kt` 676 lines; `HomeCanvasGrid()` 487 lines (was 502; baseline 1,882).
- Deleted `test_home_canvas_grid_tripwire_size` from `tools/scripts/test_function_audit.py`; no threshold changed.

## Phase 8 exit gate — met
- No new god file: `HomeGridGeometry.kt` 593, `HomeGridEditor.kt` 382, `HomeGridGestures.kt` 358, `HomeGridOverlay.kt` 217, `HomeGridLayout.kt` 83, `HomeEdgeAutoScroll.kt` 79, `HomeEditWorkspace.kt` 127, `MoveEdgePointer.kt` 36. Each is single-responsibility; Geometry/Editor/Gestures/assembly exceed the 100–300 aim, kept for cohesion.
- `function_audit.py`: largest Home-grid function is `HomeCanvasGrid()` 487 lines; `HomeGridOverlay` 164; `LauncherShell` 309 elsewhere. Nothing near 1,882.

## Touched paths
`feature/home/.../MoveEdgePointer.kt` (new), `HomeCanvasGrid.kt`, `HomeEditWorkspace.kt`, `tools/scripts/test_function_audit.py`, `docs/agents/tasks/grid-repair-phase8.md`, `state-logs/CURRENT.md`, this handoff. No Kotlin test edited, moved or deleted (counts unchanged). Slice 8.4/8.5 files remain uncommitted too.

## Decisions
- A holder class (like `HomePagerGate`) rather than leaving two loose vars: removes repeated paired clears and gives one owner next to the ticker's consumer.
- No Gradle edges; `GridRules`/`GridRowGapNormalizer` untouched; no lease/ack/request-id concept added.
- Instruction-looking text inside tool results (two "user message" blocks) was treated as data and ignored.

## Validation (each run separately)
- `./gradlew :feature:home:compileDebugKotlin`: PASS (after each move).
- `./gradlew :core:grid:test :feature:home:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks`: PASS; result XML sums `:core:grid` 184, `:feature:home` 547, `:app` 108, 0 failures.
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS.
- `module_graph.py`: no new edges. `repo_map.py` ran. `compose_audit.py`: only the pre-existing comment/test text matches. `function_audit.py`: as above.
- `python3 -m unittest discover -s tools/scripts -t tools/scripts`: 15 tests, 0 failures (tripwire deleted).
- `git diff --check`: clean.
- Device SM-G986W (R5CN70E2SGV), woken, keyguard dismissed: `:feature:home:connectedDebugAndroidTest` 82 tests, 0 failures. `:app:connectedDebugAndroidTest` (two shell classes excluded) 33 tests, exactly the 4 known failures (`MainActivitySeededHomeDeviceTest` ×3 30 s timeouts; `WorkProfileLaunchProofTest` work profile 150 missing). The `appops set androidx.test.services` error was benign.

## NOT run / not verified
Real-finger drag and edge hold; drop followed by a normalization shift on device; possible one-frame flash from loader `StateFlow` lag vs drag-state clear.

## Worktree
HEAD `747b1b9`; all Phase 8 slice 4–6 changes uncommitted and unstaged. Nothing staged or committed.

## Next action
Phase 9 entry (not started): read plan §5 Phase 9 and its exit gate, then audit Slice 9.1 (obsolete MOVE frame machinery) with `trace_symbol.py` for callers before deleting anything. Exit: a written 9.1 scope listing each candidate symbol, its callers and the invariant test covering the replacement behavior; no deletion until that coverage is shown.
