# Home grid repair Phase 5 — replace Home rendering with absolute placement

Authority: `../../lakehead-grid-repair-plan.md` §3 (contract items 6–9 are the anchors; 1–15 apply to every slice), §5 Phase 5 (slices 5.1–5.5), §9 non-goals.

Status: **implemented, unit- and device-tested (2026-10-06), uncommitted.** Evidence in `../../../state-logs/handoffs/2026-10-06-grid-repair-phase5.md`.

## Ownership

`:feature:home` only (presentation). Legality stays in `:core:grid` (`GridRules` untouched by this phase). `HomeGridMetrics` remains the single logical↔pixel owner. No Gradle edge was added or removed. The shared `WindowedSpanContainer`, `GridRowWindow`, `TileBounds.signedBounds` and `ResizeCandidateOverlay` primitives and their tests remain in `:core:designsystem`/`:core:grid` with no Home production caller; Phase 9 owns deleting them.

## Serialized acceptance

1. **5.1 `HomeGridLayout`.** `HomeGridLayout.kt` composes **every** tile and places it at `HomeGridMetrics.signedFrame(position, renderedSpan)` (which is `rect()` rounded). No row filtering, no `minRow`/`rowOrigin` rebasing, no outer offset modifier. `renderedSpan` is the held resize preview for the active tile; positions are always committed (a MOVE is shown by an outline). Gate: a tile at row N renders from row N whichever other rows are occupied (`HomeAbsoluteLayoutTest`, device, rows 16 and 1000; `HomeGridLayoutTest`, pure).
2. **5.2 Truthful content height.** Measured height is `max(minHeightPx, lowest tile bottom + bottomClearancePx)` (`homeGridContentHeightPx`); the normal floor is the full viewport. The real `ScrollState.maxValue` is the scroll range: the shadow `Viewport`/`preserveAnchorOnResize` state and the clamp effect were removed from the composable and scroll is enabled by `scrollState.maxValue > 0`. Gate: scroll range equals content bottom minus viewport (device).
3. **5.3 Centring only as presentation.** `rememberRestOffsetYPx` (`HomeRestOffset.kt`) glides the rest offset toward the committed target while not editing and **freezes it for the whole edit session**; the one `gridMetrics` built from it renders tiles/overlays and maps pointers. A drag whose frame differs from the current metrics is cancelled (`HomeEditState.cancelDragIfFrameChanged`), never rebased. **Deleted:** `rebaseMetrics`, `rebaseAndMoveTo`, `HomeDragHost.liveMetrics`, `tickMetricsState`, the ticker's rebase calls, and the edit-layout rest recomputation (`rest` while a MOVE frontier is active). Gate: entering edit mode and holding a drag do not move the frame; a two-stride drag from a centred short grid commits row 2 (device).
4. **5.4 Overlays on the same rectangles.** Candidate outlines, the resize handle, the remove control, the shortcut menu and gesture hit regions all derive bounds from `HomeGridMetrics`. The layout now fills the available width, which fixes clipping of the corner controls beside the rightmost tile. Gate: outline equals tile rect; controls hold their tile corners at a nonzero row, column and rest offset (device).
5. **5.5 Remove Home row-window rendering.** Removed `visibleWindowFor`, `HomeRenderProjection.resolveWindow`, `HomeRenderState.Loaded.window`, `HomeViewportInputs.visibleRows/overscanRows`, `HomeCanvas/HomeCanvasGrid overscanRows`, and `MainActivity`'s window argument. Gate: grep of `feature/home/src/main` and `app/src` for `GridRowWindow|WindowedSpanContainer|SpanChild|visibleRows|overscan|rowOrigin|rebaseMetrics|liveMetrics` is empty.

## Behavior changes made deliberately

- Home composes all tiles; far rows exist before they are scrolled to (tests that asserted omission were rewritten to assert existence plus on-screen reachability).
- The animated rest offset no longer moves under a drag or an active MOVE frontier.
- A structural frame change (width adaptation, rotation) during a drag cancels it with no commit.
- The grid container fills the available width instead of wrapping the rightmost tile.

## Verification and exclusions

Per `AGENTS.md` §6 plus `:feature:home` device instrumentation. `test_home_canvas_grid_tripwire_size` stays red until Phase 8. Not done here (Phase 6): auto-scroll ticker, `moveScrollBaseline`, the edit frontier and `editScrollMaxPx`. Not done here (Phase 7): the acknowledgement lease and frontier retirement. `GridRules`, `FeedOverlayGatewayImpl.kt` untouched; no virtualization.
