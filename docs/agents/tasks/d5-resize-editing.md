# D5 — Resize Editing

Status: COMPLETE
Owner: `:feature:home` (session + gestures), `:core:designsystem` (handles + overlay), `:app` (wiring)

## Objective

Add tile resize editing to the home grid. Long-press enters edit mode showing resize handles on the selected tile. Dragging a resize handle changes the tile's span; dragging the tile body moves it (D4, already working). Valid resizes persist on commit; invalid resizes snap back.

## Interaction model

- Long-press on a launchable tile enters edit mode (unified session).
- In edit mode: selection border + resize handles visible on active tile.
- Body drag = move (existing D4 behavior, must not regress).
- Handle drag (bottom-right corner) = resize. Position stays fixed, span changes.
- Back or tap-outside cancels edit mode, writes nothing.
- On valid resize release: commit via `onResizeTile(itemId, span)`.
- On invalid resize release (collision/OOB): snap back, stay in edit or exit.

## Renderable spans

Grid accepts any positive-integer span. UI offers drag targets: 1×1, 2×1, 1×2, 2×2, 4×2.
`ResizeDragMath` computes raw span from drag delta; `RenderableSpans.quantize` maps to nearest renderable entry.

## Files to create

| Module | File | Purpose |
|---|---|---|
| `:feature:home` | `HomeEditSession.kt` | Unified edit session (Idle/Active with MOVE/RESIZE mode) |
| `:feature:home` | `ResizeDragMath.kt` | Pixel drag to raw span conversion |
| `:feature:home` | `RenderableSpans.kt` | UI span allowlist + quantize |
| `:core:designsystem` | `ResizeHandles.kt` | Bottom-right corner resize affordance |
| `:core:designsystem` | `ResizeCandidateOverlay.kt` | Ghost frame preview during resize drag |

## Files to modify

| Module | File | Change |
|---|---|---|
| `:feature:home` | `HomeCanvasGrid.kt` | Replace `moveSession` with `editSession`, add resize handle + overlay, add `onResizeTile` param |
| `:feature:home` | `HomeCanvasTile.kt` | Add `isEditActive`, pass through edit state |
| `:feature:home` | `HomeCanvas.kt` | Add `onResizeTile` pass-through |
| `:app` | `HomeRouteLoader.kt` | Add `commitResize` (mirrors `commitMove`) |
| `:app` | `LauncherShell.kt` | Add `onResizeTile` param |
| `:app` | `MainActivity.kt` | Wire `onResizeTile` to `commitResize` |

## Files to delete (at cutover)

| Module | File | Reason |
|---|---|---|
| `:feature:home` | `MoveEditSession.kt` | Replaced by `HomeEditSession` |
| `:feature:home` | `MoveDragMath.kt` | Logic absorbed into HomeEditSession |

## Tests

| File | What |
|---|---|
| `HomeEditSessionTest.kt` | State machine: enter, beginResize, updateMoveCandidate, updateResizeCandidate, validity transitions, cancel, hasMoved, hasResized |
| `ResizeDragMathTest.kt` | Pixel-to-span: 1-cell right gives 2x1, diagonal gives 2x2, sub-cell jitter, zero-stride guard, quantize mappings |
| `HomeCanvasGridResizeTest.kt` | Compose: handles visible on edit, handle drag changes span, invalid overlay, commit/cancel, 48dp target |
| `HomeRouteLoaderTest.kt` | commitResize: IO dispatch, reload on Accepted, no reload on Rejected |

## Exit criteria

1. Long-press enters edit; handles appear; body-drag moves (D4 intact); handle-drag resizes.
2. Valid resize persists span, survives restart, preserves unrelated tiles.
3. Invalid resize and cancel write nothing, snap back.
4. Thin passthrough wiring (no logic in :app, no DB in composables).
5. All 9 validation commands pass.
6. Device proof: resize 1x1 to 2x2, colliding resize rejected, cancel restores.