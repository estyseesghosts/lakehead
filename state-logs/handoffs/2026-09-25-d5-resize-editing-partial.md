# 2026-09-25 — D5 resize editing (partial, paused)

Status: PAUSED — implementation complete, device proof pending
Owner/slice: `:feature:home` / D5 resize editing; `:core:designsystem` / resize primitives; `:app` / wiring

## Baseline and worktree

Prior commit: `0b30a34` (D4 move editing). D5 changes landed on top but not yet committed.

## Completed

### New files created (5)
- `feature/home/.../HomeEditSession.kt` — Unified edit session (Idle/Active with MOVE/RESIZE mode)
- `feature/home/.../ResizeDragMath.kt` — Pixel drag to raw span conversion
- `feature/home/.../RenderableSpans.kt` — UI span allowlist (1×1, 2×1, 1×2, 2×2, 4×2) + quantize
- `core/designsystem/.../ResizeHandles.kt` — Bottom-right corner resize affordance (48dp touch target)
- `core/designsystem/.../ResizeCandidateOverlay.kt` — Ghost frame preview during resize drag

### Files modified (6)
- `feature/home/.../HomeCanvasGrid.kt` — Replaced `moveSession` with `editSession`, added resize handle + overlay, added `onResizeTile` param
- `feature/home/.../HomeCanvasTile.kt` — `isMoveActive`→`isEditActive`, `onEnterMove`→`onEnterEdit`
- `feature/home/.../HomeCanvas.kt` — Added `onResizeTile` pass-through
- `app/.../HomeRouteLoader.kt` — Added `commitResize` (mirrors `commitMove`)
- `app/.../LauncherShell.kt` — Added `onResizeTile` param
- `app/.../MainActivity.kt` — Wired `onResizeTile` to `commitResize`

### Files deleted (2)
- `MoveEditSession.kt` — replaced by `HomeEditSession.kt`
- `MoveEditSessionTest.kt` — replaced by `HomeEditSessionTest.kt`

### Tests (46 new)
- `HomeEditSessionTest.kt` — 29 tests (state machine, validity transitions, cancel, hasMoved, hasResized)
- `ResizeDragMathTest.kt` — 14 tests (pixel-to-span, quantize mappings)
- `HomeRouteLoaderTest.kt` — +3 tests (commitResize orchestration)
- `HomeCanvasTest.kt` — +1 test (resize handle visibility)

## Validation

- `./gradlew clean assembleDebug` ✅
- `./gradlew installDebug` ✅ — APK installed on SM-G986W (R5CN70E2SGV, API 33)
- `./gradlew test` ✅ (reported by implementation agent)
- `./gradlew lint` ✅ (reported by implementation agent)
- `repo_map.py` ✅ (reported by implementation agent)
- `module_graph.py` ✅ (reported by implementation agent)
- `function_audit.py` ✅ (reported by implementation agent)
- `compose_audit.py` ✅ (reported by implementation agent)

## Device proof

**NOT YET DONE.** APK is installed but no device interaction has been verified. Need:
1. Long-press enters edit mode, resize handles visible
2. Handle drag resizes 1×1→2×2, persists after restart
3. Colliding resize rejected (snap back, nothing written)
4. Cancel restores original layout
5. Body drag still moves (D4 unregressed)

## Remaining and next action

Next operation: Run device proof on SM-G986W. If green, commit D5 slice. If issues found, fix and re-test.