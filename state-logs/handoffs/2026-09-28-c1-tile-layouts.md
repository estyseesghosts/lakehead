# Handoff — C1 tile layouts (01.md items 8+9), slice `five` implementation

- Date: 2026-09-28. Owner: subagent `05` (front-end). Scope: tile UI only.
- Baseline: working tree already contained uncommitted concurrent work (A5 themed
  helper + remove tests in `HomeCanvasTest`, `MainActivity` B-surface wiring,
  agent-file edits). Nothing foreign was reverted or overwritten; edits are
  additive except the retired count-only fixture params (see below).
- `state-logs/CURRENT.md` left untouched (orchestrator owns it; A2-S1 still
  device-pending there).

## Completed work (all code-complete, unit/device-tested as noted)

1. **`core/grid/ShortcutStripGeometry`** — layout contract additions, capacity
   unchanged (`usableEdgeCells-1` already held): new `PinnedStripOrientation`
   (`orientationFor`: narrow 1×N → VERTICAL, everything else HORIZONTAL),
   `stripGroupExtentPx(extent, pitch, slots)` (overflow-checked group width for
   mid-alignment), `centeredGroupStartPx(container, group)` (truncating
   mid-align). Docs now state columns×rows normalization (2-wide×4-tall → 1,
   4-wide×2-tall → 3, never transposed).
2. **`core/designsystem`** —
   - `PinnedTileShortcut(id, label)` presentation model (opaque id + accessible
     label; no icon because `LauncherShortcut` carries none yet; per-tile
     distinction preserved upstream by the tile-keyed binding) +
     `pinnedShortcutDescription` ("L, shortcut") + `pinnedOverflowDescription`.
   - `AppSymbol(bitmap, modifier, extent = symbolSize)`: optional extent, Fit +
     tint kept; callers scale via the param, never a second size system.
   - `AppTile` rewritten around a real `pinnedShortcuts: List<PinnedTileShortcut>`
     (pre-clamped, pin-order, index 0 nearest app) + `overflowCount` +
     `showOverflowIndicator` + `onShortcutClick(id)`; `shortcutPlaceholders` and
     `pinnedCount` count-fixtures **retired**. Icon slot is now
     extent-aware (`@Composable (Dp) -> Unit`). Zero pins → centered on every
     span (1×1 full-bleed; small spans reference size; large spans
     `min(availW,availH)` after padding via `BoxWithConstraints`, no strip
     reserve). Pinned → H bottom-centered `Row` (app rightmost) on wide/square,
     right-edge `Column` (app bottom-right, upward, same pitch) on narrow;
     glyphs at `unitIconSize` with outline-marker distinction; overflow `+N`
     non-launchable (no click, no Role) or merged into TileBody
     `stateDescription` when hidden (edit-selected / zero-capacity); strip glyphs
     use conditional `clickable` (disabled in edit → Role+label kept, no action).
     No visible text labels (shortcuts.md §24 icon-only); labels live in
     semantics (TalkBack-distinct). `isLargeTileSpan` shared rule (complement of
     the A5 1×1/1×2/2×1 small set). `symbolAlignment(span, pinned=true)` now
     `BottomEnd` on all spans (right-edge V contract; C1 supersedes grid.md §4 —
     doc amended, see below).
3. **`feature/home`** — new `TilePinnedStrip`: `resolve(binding, shortcuts)`
   joins B2 sources in binding (pin) order, drops stale refs from rendering
   (binding retains them); `arrange(span, resolved, isEditSelected)` delegates
   capacity to `TileShortcutPolicy` (core:grid) and windowing to
   `ShortcutOrdering` (genericized `visible/overflow/resize/restore` to `<T>`,
   2-line test inference fix), hides the `+N` node only when edit-selected
   (strip stays rendered under floating controls; count in body semantics),
   never deletes on shrink. `HomeCanvasTile` consumes `pinnedShortcuts` +
   `onShortcutClick`, arranges on the live (preview) span; `HomeCanvasGrid` /
   `HomeCanvas` gain `pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap()`
   + `onPinnedShortcutClick` pass-through (defaults pin-free; **:app not wired —
   deliberate, see NOT-RUN**).
4. **Docs**: `docs/grid.md` §4 amended with the C1 pinned-strip contract
   (centered-large default, compact anchors, capacity examples, overflow rule).

## Decisions / rationale

- Semantics-not-visible-text for shortcut labels: shortcuts.md §24 (icon-only,
  invariant) vs slice "own labels" → labels as discrete TalkBack nodes +
  visible marker ring; acceptance "TalkBack distinct" supports this reading.
- `PinnedTileShortcut` is plain `(id, label)`, not wrapping `ShortcutRef`:
  `core:model` is not on designsystem's compile classpath (grid declares it
  `implementation`), and the slice's `PinnedShortcut(id,icon,label)` shape needs
  no profile fields at presentation level.
- Existing `anchorFor/directionFor/maxSlots/slotPitch/slotCenter/visibleCount`
  kept (compat); new orientation/group math added alongside.
- `function_audit` size flag on `AppTile` (110 lines) accepted: splitting a layout
  composable on line count alone is forbidden; contract stays narrow.

## Validation — exact results

- `./gradlew :core:grid:testDebugUnitTest` SUCCESS (40 tests: 12 new).
- `./gradlew :core:designsystem:testDebugUnitTest` SUCCESS (incl. new
  `PinnedTileLayoutTest` 10 + updated `SymbolAlignmentTest`).
- `./gradlew :feature:home:testDebugUnitTest` SUCCESS (incl. new
  `TilePinnedStripTest` 11; `ShortcutOrderingTest` 2-line inference fix).
- `./gradlew test` SUCCESS (263 tasks), `./gradlew assembleDebug` SUCCESS,
  `./gradlew lint` SUCCESS, `:core:designsystem:assembleDebugAndroidTest` +
  `:feature:home:assembleDebugAndroidTest` SUCCESS.
- `./gradlew installDebug` SUCCESS on SM-G986W (R5CN70E2SGV).
- `:core:designsystem:connectedDebugAndroidTest` (AppTileTest filter, device):
  all 10 new + all edited C1 tests PASS (centering, 304.dp large extent,
  bottom-centered app+3 group, right-edge upward, overflow node + hidden
  semantics, disabled glyphs, distinct routing). 12 remaining failures are all
  in byte-identical untouched bodies: missing-`LauncherTheme` crashes,
  double-`setContent`, pre-existing fetch-in-`runOnIdle` nesting.
- `:feature:home:connectedDebugAndroidTest` (HomeCanvasTest filter, device):
  all 3 new strip tests PASS. 6 remaining failures, none mine (diff-proven):
  4× pre-existing fetch-in-`runOnIdle` nesting in untouched bodies,
  `publicSignatures` reflection (Compose mangles all composable JVM names —
  `HomeCanvas-8_KINIk` — so `name == "HomeCanvas"` never matches; pre-existing),
  `removeAffordance_smallTile_topRightCorner` 180-vs-144 geometry (A5 author's
  uncommitted test; zero diff in remove positioning / 1×1 pin-free tile paths
  from this slice — flagged for A5 owner).
- Audits: `repo_map`, `module_graph` (no new edges), `function_audit`,
  `compose_audit` (comment/string false positives only) run.
- Paparazzi: NOT AVAILABLE (no plugin in repo) — device compose tests cover the
  slice's screenshot list instead (1×1/1×2/2×1, 2×2 large, 4-wide app+3,
  vertical upward, overflow, disabled/edit strip).
- NOT-RUN on device: live pinned strip during move/resize, resize recompute
  with real pins, live TalkBack traversal — `:app` does not yet feed
  `pinnedByTile` (defaults empty; B-surface wiring is the B-owner's call).
  Large-no-pins centering is device-proven via the on-device extent test.

## Known follow-ups (not this slice)

- `disabledInteraction_stripsActions…` (untouched) will still fail on its OnClick
  assertion after its nesting bug is fixed if `combinedClickable(enabled=false)`
  retains OnClick like `clickable` does — edit-behavior owner to check.
- `publicSignatures` guard needs `startsWith` (or equivalent) for mangled
  composable names — test owner.
- A5 `removeAffordance_smallTile` geometry on SM-G986W (180 vs 144) — A5 owner.

## Worktree state

- Uncommitted (mine + pre-existing concurrent): 10 main files + 3 test files
  (see `git diff --stat`); deleted
  `feature/home/.../HomeCanvasTilePinnedTest.kt` (referenced retired helpers;
  coverage moved to `TilePinnedStripTest`); new `PinnedTileShortcut.kt`,
  `TilePinnedStrip.kt`, `TilePinnedStripTest.kt`, `PinnedTileLayoutTest.kt`.
  Nothing staged/committed/pushed.

## Next action (exit-conditioned)

- B-owner wires `:app` (`ShortcutSurfaceLoader` binding + gateway → per-tile
  `TilePinnedStrip.resolve` → `HomeCanvas.pinnedByTile`, `onPinnedShortcutClick`
  → `gateway.launch` off-main with tile-span capacity gate already in
  `togglePin`); exit when a 2×2 tile with 2+ pins on SM-G986W shows app+1 and a
  `+N` node, survives resize without losing pins, and TalkBack announces app vs
  shortcuts distinctly — or a concrete failure is filed as a bounded follow-up.
