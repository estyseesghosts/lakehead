# Widgets as Home tiles: slices 0, 1, 2, 4 (2026-10-09)

Task: `docs/agents/tasks/widgets.md` (plan origin `~/.opencode/plan/widgets.md`). Done ahead of roadmap order by user decision.

## Completed
- Slice 0: task spec and doc notes (`docs/agents/tasks/widgets.md`, `docs/architecture.md`, `docs/platform-capabilities.md`).
- Slice 1: free resize. `RenderableSpans` deleted; `ResizeDragMath.clampSpan` bounds width to the grid only; legality stays in `GridRules`. Shortcut strip and remove-control placement already generic over span.
- Slice 2: long-press on empty background enters `Editing(selected = null)` (`HomeEditState.enterOnBackgroundLongPress`, detector in `HomeCanvasGrid`, ignores presses that land on a tile). Empty home now composes the grid; the "Home is empty" text and `gridHoldSignal` were removed (hold = `isHomeHold`).
- Slice 4: `firstFreeRect` (`GridFirstFreeRect.kt`) and `widgetDefaultSpan` (`GridWidgetDefaultSpan.kt`) in `:core:grid`, with tests.

## Touched
core/grid (2 new + tests, `overlaps` made internal), feature/home (`ResizeDragMath`, `HomeGridEditor`, `HomeCanvasGrid`, tests, `HomeCanvasTest` comment), `MainActivitySeededHomeDeviceTest` (empty-home assertion now checks the grid node), docs.

## Validation
- `:feature:home:testDebugUnitTest` PASS; `:core:grid:test` PASS (by subagents); `assembleDebug` PASS (after slice 1); `:app:compileDebugAndroidTestKotlin` PASS.
- NOT RUN: `lint`, `:app:testDebugUnitTest`, all device tests (no device attached), audits.
- Unverified on device: dragging a tile to 5x1/3x1; background long-press vs scroll/pager; empty-home behaviour.

## Next
Slice 3: new `:platform:widgets` module with provider discovery. Exit: mapping unit tests pass and module graph shows only `platform -> core` edges.
