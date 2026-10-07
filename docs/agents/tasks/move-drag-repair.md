# Move-drag repair

Owner: `:feature:home` (gesture, lift, auto-scroll, ghost); `:app` `HomePlacementReconciliation.kt` (optimistic move). No new module edges, no `:core:grid` rule changes.

## Contract

1. **Start.** A launchable tile touched before edit mode is picked up with no slop when its long-press fires with the finger still down. In edit mode a drag starts on plain radial touch slop in any direction (a true 45° works). `classifyHomeAxis` remains for idle pager arbitration only.
2. **Follow.** The lifted tile renders at `pointer + scroll − grabOffset`, slightly raised (scale 1.06, shadow). The translation is applied in a `graphicsLayer {}` block reading `MoveEdgePointer`, so pointer events do not recompose the grid. The outline stays as the drop-target ghost. Corner controls (resize handle, remove button) are hidden during a move.
3. **Snap.** Moves map the top-left to the *nearest* cell (`HomeGridMetrics.nearestTopLeftCell`). The floor mapping remains for hit-testing only.
4. **Honest drop.** The ghost shows the position after `GridRules.move` + `GridRowGapNormalizer.normalize` (`DragState.Move.dropPosition`). `:app` `withRequestedMove` applies the same computation, so the first emission equals the accepted grid; a rejected move applies nothing optimistically. Tiles glide (`PlacementGlide`, 220 ms) from their old visual spot to a new logical cell; a released lift glides from the finger.
5. **Auto-scroll.** Bands are measured from the real viewport edges (72 dp). Scroll is continuous per frame, speed proportional to depth (0 at the inner boundary, 720 dp/s at the edge). Frames are requested only while the pointer is inside a band. The workspace is the committed bottom + 2 empty rows + the tile's span rows (`moveWorkspaceBottomRow`); short content therefore gains no scroll range. On release the scroll animates back inside the committed range before the workspace is dropped.

## Acceptance

- Unit: `TouchSlopTest`, `MoveDragMathTest` (nearest/symmetric/hit-test), `MoveSettleAndLiftTest`, `HomeAutoScrollTest` (velocity, bands), `HomeEditWorkspaceTest` (cap), `HomePlacementReconciliationTest`, `HomeRouteLoaderTest`.
- Device (`:feature:home:connectedDebugAndroidTest`): long-press-then-drag in one gesture; true 45° move; continuous per-frame auto-scroll; capped workspace.
- Real-finger pass on a device: pickup, follow, edge scroll, drop glide, rejected glide-back, and a scrolled-then-drag coordinate check (pointer positions are treated as viewport-local; verify there is no double-counted scroll).
