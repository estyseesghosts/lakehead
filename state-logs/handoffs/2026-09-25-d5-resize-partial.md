# 2026-09-25 — D5 resize editing (partial, paused)

Status: PAUSED — resize works, move works at top, scroll offset bug remains
Owner/slice: `:feature:home` / D5 resize editing

## What works

- Resize: long-press tile, drag bottom-right corner handle → span changes (grow and shrink)
- Continuous resize: after commit, session stays active in MOVE mode, can resize again without re-long-press
- Move: long-press tile, drag body → tile moves (works at scroll=0)
- Cancel: back button or tap outside exits edit mode
- Handle hit-test: correct at scroll=0, uses absolute coords with scroll offset
- Move flicker during drag: FIXED (was caused by competing gesture detectors)

## What doesn't work

1. **Move overlay scroll offset**: When scrolled down, the blue move candidate outline is ~200px below the finger. At scroll=0 it tracks correctly. The pointer math (`yPx = viewport + scrollState.value`, `windowFirstRow = rowOrigin = 0`) looks correct on paper, but the overlay is still offset. Need to investigate whether `scrollState.value` inside the `pointerInput` lambda is stale, or whether `next.position.y` is not viewport-local as assumed.

2. **Flicker on commit**: `HomeRouteLoader.reload()` emits `Loading` which unmounts `HomeCanvasGrid` entirely, destroying scroll position and edit state. This causes a flash after move/resize commit. Fix: don't emit `Loading` on reload, or debounce it.

3. **Per-candidate recomposition**: Every drag tick rebuilds the `children` list with new lambdas, causing all visible tiles to recompose and `WindowedSpanContainer` to remeasure. Not a correctness bug but causes jank.

## Architecture of the unified drag handler

The current `HomeCanvasGrid` uses a single `awaitEachGesture` handler (not `detectDragGestures`) to avoid parent/child gesture competition:

```kotlin
val dragModifier = Modifier.pointerInput(active?.itemId) {
    awaitEachGesture {
        if (sessionState.value is HomeEditSession.Active) {
            // Already in edit mode — use awaitPointerEvent() to catch already-pressed pointer
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
            // ... slop detection, hit-test (handle vs body), route to RESIZE or MOVE
        } else {
            // Not in edit mode — wait for fresh press (long-press handled by tile's combinedClickable)
            val down = awaitFirstDown()
            // ... long-press timer, drag detection
        }
    }
}
```

Key design decisions:
- `pointerInput` keyed on `active?.itemId` only (stable during drag, prevents restart)
- `awaitPointerEvent()` instead of `awaitFirstDown()` when already in edit mode (pointer already down from long-press)
- Handle is visual-only (no own pointerInput) — parent unified handler does all gesture routing
- Hit-test checks if drag start is on the 48dp handle area → RESIZE, else → MOVE
- Origin corner snapshotted at gesture start (not live candidate)
- `rowOrigin` pinned to `0L` (constant `HOME_CANVAS_ROW_ORIGIN`) — all coords in absolute space

## Files changed (uncommitted)

### New files
- `feature/home/.../HomeEditSession.kt` — unified edit session (replaces MoveEditSession)
- `feature/home/.../ResizeDragMath.kt` — pixel drag → raw span conversion
- `feature/home/.../RenderableSpans.kt` — UI span allowlist + quantize
- `core/designsystem/.../ResizeHandles.kt` — transparent 48dp hit target (visual removed)
- `core/designsystem/.../ResizeCandidateOverlay.kt` — ghost frame during resize
- `feature/home/src/test/.../HomeEditSessionTest.kt` — 29 tests
- `feature/home/src/test/.../ResizeDragMathTest.kt` — 14 tests
- `feature/home/src/test/.../VisibleWindowForTest.kt` — 9 tests
- `feature/home/src/test/.../ContentMinHeightPxTest.kt` — 7 tests

### Modified files
- `feature/home/.../HomeCanvasGrid.kt` — unified drag handler, resize wiring, rowOrigin=0
- `feature/home/.../HomeCanvasTile.kt` — isEditActive, onEnterEdit
- `feature/home/.../HomeCanvas.kt` — onResizeTile pass-through
- `app/.../HomeRouteLoader.kt` — commitResize
- `app/.../LauncherShell.kt` — onResizeTile param
- `app/.../MainActivity.kt` — wire onResizeTile

### Deleted files
- `feature/home/.../MoveEditSession.kt` — replaced by HomeEditSession
- `feature/home/src/test/.../MoveEditSessionTest.kt` — replaced by HomeEditSessionTest

## Validation performed

- `./gradlew clean assembleDebug` ✅
- `./gradlew installDebug` ✅ (SM-G986W, R5CN70E2SGV)
- `./gradlew test` ✅ (reported by implementation agents)
- `./gradlew lint` ✅ (reported by implementation agents)
- Module graph clean, no feature→data edge

## Key investigation findings

### Flicker root cause (from three)
1. Per-candidate full recomposition: `editSession` change → new `children` list + N new content lambdas → `WindowedSpanContainer` remeasures every tick
2. `Loading` interstitial: `commitMove`/`commitResize` triggers `reload()` which emits `Loading` → unmounts `HomeCanvasGrid` → scroll reset, edit state lost

### Scroll offset investigation (from zero)
- Math trace shows `yPx = viewport + scrollState.value` with `windowFirstRow = rowOrigin = 0` should be correct
- `rowOrigin` is pinned to `0L` — all coords are absolute space
- Concrete example (viewport y=200, scroll=500, stride=100): overlay should be at viewport y=200, tracking finger
- But user reports ~200px offset when scrolled. Root cause still unknown.
- Possible causes: `scrollState.value` stale inside pointerInput, `next.position.y` not viewport-local, or some other coordinate space mismatch

## Next action

Investigate and fix the move overlay scroll offset. Use zero to explore the actual coordinate values. Use three to plan the fix. Use five to implement. Use three to review. Use one to commit after device verification.

After that fix, address the Loading interstitial flicker. Then run all 9 validation commands and commit the D5 slice.