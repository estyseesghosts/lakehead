# 2026-09-28 — C1 review FAIL fixes (tile overlap/overflow, small-span anchors, grid-unit pitch)
Status: completed (code + unit/device tests; device-interaction NOT-RUN as noted)
Owner/slice: `05` front-end, tile UI only (`core/designsystem` + `:feature:home` arrangement + grid-capacity use; no B/app rewiring) — `docs/agents/tasks/01.md` C1 card.

## Baseline and worktree
- Baseline: working tree already held uncommitted concurrent work (agent-file edits, `MainActivity`/B-surface wiring, `core/grid` strip-geometry edits, `HomeCanvasGrid`, `docs/grid.md`, `state-logs/CURRENT.md`). None of it reverted, overwritten or relied upon; my diff touches only the files listed below. Nothing staged/committed/pushed.
- `state-logs/CURRENT.md` left untouched (orchestrator owns it; A2-S1 still device-pending there).

## Completed and decisions
Fixed the three C1 review FAILs, keeping every listed invariant (binding order wins, stale retained-not-rendered, shrink preserves, capacities 2×2/4×2/2×4 orientation, H bottom-centered app-rightmost index0-nearest, V upward same pitch recomputed, +N non-launchable, never delete, app distinct TalkBack, disabled-in-edit, no Material/labels, app pin-free defaults):
1. **P1 overlap/overflow** — new `core/designsystem/.../PinnedStripFit.kt`: pure density-free `Dp` fit (`fitPinnedStrip` + `PinnedStripFit` + `stripPitch`). `AppTile` (outer `Box`→`BoxWithConstraints`) fits the strip to the measured tile: visibles drop far-end-first into the overflow total, the `+N` node renders only when it too fits, otherwise the total merges into TileBody `stateDescription`. New `isEditSelected` param (default false; `HomeCanvasTile` passes `isSelected || isEditing`) reserves edit insets mirroring grid control geometry — resize corner half (24.dp) on the strip side, top-right corner on small spans, centered 48.dp box on large spans — withholding glyphs down to the whole strip rather than overlapping (e.g. edit-selected 2×2 collapses to semantics; edit 1×2 keeps the app above the resize corner). Caller retains everything; resize restores.
2. **P2 small spans** — restored the unpinned anchor (`symbolAlignment(span, hasPinned=false)` → 1×2 `BottomCenter`, else `BottomEnd` per `docs/grid.md` §4; large centered-extent path untouched) and the `AppTile` zero-pin small branch (1×1 full-bleed centered hit-box unchanged; 1×2 bottom-center / 2×1 bottom-right reference-size symbols). Fixed the tests that enshrined centering (`zeroPinned_narrowTile…`, `noPinnedShortcutsIsAlwaysCentered`).
3. **P2 pitch** — strip pitch now derives from the measured edge plus the shared gap (`stripPitch = (edge + gap) / cells`, the `Dp` form of `core:grid` `slotPitchPx`, cross-checked in tests); glyphs stay at `unitIconSize` with `(pitch − extent)` gaps. Full-capacity groups exactly fill realistic padded edges (2×2: 40+64=104=120−16); retired hard-coded `PinnedStripGap`.
- Notable finding: with grid-unit pitch, full-capacity + node can never coexist on small tiles, so the fit maximizes tappable visibles and announces the remainder (preserves shortcut labels in TalkBack); the visible `+N` renders whenever it fits (e.g. 4×2 slack states, covered by tests).
- Test-only fix: `wideSpanPinnedShortcuts…` body tap moved to a strip-clear point — `performClick()` taps node center, which sits exactly under the middle glyph on realistic tiles (correct hit behavior, wrong test assumption).
- Touched: `AppTile.kt`, `AppSymbol.kt`, `PinnedStripFit.kt` (new), `HomeCanvasTile.kt` (passes `isEditSelected`), `PinnedStripFitTest.kt` (new, 15 tests), `SymbolAlignmentTest.kt`, `AppTileTest.kt` (realistic 56/120/184/248.dp geometry + 5 new tests, incl. pitch-adaptation and edit no-overlap), `HomeCanvasTest.kt` (one overflow test → semantics expectation; `homeCanvasTile_showsOverflowIndicator_beyondCapacity` renamed to `homeCanvasTile_overflowBeyondCapacity_announcedInSemantics`). `TilePinnedStrip`, grid capacity, control positioning, B/app wiring untouched.
- `function_audit` size flags on `AppTile` (130) and `fitPinnedStrip` (121) accepted per protocol (layout/geometric units; never split on line count alone).

## Validation (exact)
- `:core:designsystem:testDebugUnitTest` SUCCESS (incl. 15 new `PinnedStripFitTest`, 8 `SymbolAlignmentTest`).
- `./gradlew test` SUCCESS (263 tasks); `:feature:home/:core:grid` unit SUCCESS.
- `./gradlew assembleDebug` SUCCESS; `./gradlew lint` SUCCESS (438 tasks); `./gradlew installDebug` SUCCESS on SM-G986W (R5CN70E2SGV).
- `:core:designsystem:connectedDebugAndroidTest` (AppTileTest, device): all 16 owned tests PASS (4 pre-existing-geometry + 7 resized + 5 new); 12 remaining failures are byte-identical untouched bodies (missing-`LauncherTheme`, double-`setContent`, fetch-in-`runOnIdle` nesting) matching the prior handoff's documented set.
- `:feature:home:connectedDebugAndroidTest` (HomeCanvasTest, device): renamed overflow test PASSES; other failures unchanged pre-existing (4 nesting flakes, `publicSignatures` mangled-name, A5 `removeAffordance_smallTile` 180-vs-144).
- `assembleDebugAndroidTest` for both modules SUCCESS. Audits: `repo_map`, `module_graph` (no new edges), `function_audit`, `compose_audit` (comment/string hits only).
- NOT-RUN on device: live pinned strip during move/resize with real pins, resize recompute with real pins, live strip TalkBack traversal — `:app` still does not feed `pinnedByTile` (defaults empty; B-owner wiring). Prior live-strip/TalkBack NOT-RUN carries over.

## Remaining and next action
- B-owner wires `:app` per the prior C1 handoff (exit: 2×2 with 2+ pins on SM-G986W shows app+1, overflow announced, resize preserves pins, TalkBack distinct) — unchanged, now against the fitted strip.
- Pre-existing device-test debt (other owners): 12 AppTileTest infra bodies, HomeCanvas nesting/`publicSignatures`/A5 geometry.
- Next action (05 or reviewer): confirm the overflow-preference call (tappable pin + announced remainder vs visible +N at the cost of the pin) — code currently maximizes visibles; switching is a contained change in `fitPinnedStrip` + 3 test expectations.
