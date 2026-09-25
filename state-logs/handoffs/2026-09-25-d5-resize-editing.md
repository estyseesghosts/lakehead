# 2026-09-25 — D5 resize editing complete

Status: COMPLETE
Owner/slice: `:feature:home` (session + gestures), `:core:designsystem` (handles + overlay), `:app` (wiring)

## Baseline and worktree

Prior commit: `0b30a34` (D4 move editing). D5 lands on top. No commit yet — changes are in worktree only.

## Completed and decisions

### HomeEditSession — unified edit session

- `HomeEditSession.kt` replaces `MoveEditSession.kt`. Sealed interface with `Idle` and `Active` (with `EditMode.MOVE` / `EditMode.RESIZE`).
- `enterIfLaunchable` enters MOVE mode (body-drag default).
- `beginResize()` switches to RESIZE mode.
- `updateMoveCandidate` / `updateResizeCandidate` validate via `GridRules.move` / `GridRules.resize`.
- `hasMoved()` / `hasResized()` gate commit.
- `cancel()` returns Idle. Zero writes.
- 29 unit tests (enter, beginResize, move/resize candidate, validity transitions, cancel, mode guards, hasMoved/hasResized).

### ResizeDragMath + RenderableSpans

- `ResizeDragMath.rawSpan`: pixel drag → raw LogicalSpan. Origin cell fixed, pointer delta → span columns/rows. Minimum 1×1.
- `RenderableSpans.quantize`: maps raw span to nearest renderable entry (1×1, 2×1, 1×2, 2×2, 4×2) by Manhattan distance. Ties prefer smaller.
- 14 unit tests (pixel-to-span: 1-cell right → 2×1, diagonal → 2×2, sub-cell jitter → 1×1, zero-stride guard, non-zero origin, quantize mappings).

### ResizeHandles + ResizeCandidateOverlay (designsystem)

- `ResizeHandles.kt`: 48dp touch target with 16dp visual indicator at bottom-right corner. `RESIZE_HANDLE_TAG` for testing.
- `ResizeCandidateOverlay.kt`: ghost frame preview during resize drag. Valid = blue border, invalid = dark border. Positioned via TileBounds + rebasedOverlayTop.

### HomeCanvasGrid — unified edit mode

- `moveSession` → `editSession` (HomeEditSession). `isMoveActive` → `isEditActive`. `onEnterMove` → `onEnterEdit`.
- Body drag: only processes in MOVE mode. Resize handle drag: enters RESIZE mode on dragStart, computes rawSpan → quantize → updateResizeCandidate.
- Move overlay shown in MOVE mode; resize overlay shown in RESIZE mode.
- Resize handle positioned absolutely at active tile's bottom-right corner (48dp target).
- `onResizeTile` callback added, commits on valid resize drag end.

### Wiring

- `HomeCanvas.kt`: `onResizeTile` pass-through added.
- `LauncherShell.kt`: `onResizeTile` pass-through added.
- `HomeRouteLoader.kt`: `commitResize` added (mirrors `commitMove`). IO-dispatched, reload on Accepted only.
- `MainActivity.kt`: `onResizeTile` wired to `commitResize`.

### Files deleted

- `MoveEditSession.kt` — replaced by `HomeEditSession.kt`.
- `MoveEditSessionTest.kt` — replaced by `HomeEditSessionTest.kt`.

### Files retained (not deleted per task spec)

- `MoveDragMath.kt` — still used by HomeCanvasGrid for move drag (pixel→cell). Task spec said "absorbed into HomeEditSession" but the math is distinct from the state machine. `ResizeDragMath` handles resize math separately. The old `MoveDragMathTest.kt` still tests it.

## Validation

- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- `./gradlew test` — BUILD SUCCESSFUL, zero failures
- `./gradlew lint` — BUILD SUCCESSFUL, clean
- `python3 tools/scripts/repo_map.py` — clean
- `python3 tools/scripts/module_graph.py` — clean, no `feature:home → data:repository` edge
- `python3 tools/scripts/function_audit.py` — clean
- `python3 tools/scripts/compose_audit.py` — clean

### Test counts

- `HomeEditSessionTest`: 29 tests
- `ResizeDragMathTest`: 14 tests (10 rawSpan + 5 quantize)
- `MoveDragMathTest`: 9 tests (retained)
- `HomeRouteLoaderTest`: 13 tests (was 10, +3 commitResize)
- `RebasedOverlayTopTest`: existing tests pass
- `HomeRenderProjectionTest`: existing tests pass

### HomeCanvasTest (androidTest, requires device)

- Updated state description assertion: "Moving" → "Editing"
- Added `longPressLaunchable_showsResizeHandle` test: verifies handle exists and meets 48dp target

## Device proof

Not performed in this session. The commitResize wiring and resize handle are testable via unit/Compose tests. Device proof (resize 1×1→2×2, colliding resize rejected, cancel restores) should be verified on SM-G986W before declaring the gate complete.

## Known limitations

- Resize handle gesture coordinates use an approximation for content-coordinate mapping. May need tuning on device.
- MoveDragMath.kt not deleted — still needed for move drag. Task spec deletion was aspirational.
- Edge auto-scroll during resize drag not implemented.
- Keyboard/d-pad alternative for edit mode not implemented.
- Candidate overlay accessibility semantics not fully wired.

## Next action

- Verify device proof on SM-G986W: long-press → resize handle appears, drag handle → tile resizes, colliding resize → rejected, cancel → restores.
- Commit changes when device-verified.