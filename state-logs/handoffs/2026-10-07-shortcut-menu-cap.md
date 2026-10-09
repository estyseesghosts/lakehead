# 2026-10-07 — Cap the edit shortcut menu to the tile's roomier side (close-out)

Status: completed (Slice 4 problem-tile manual pass not run — needs the user's tile/data)
Owner/slice: `:feature:home`, slices 1–3 + 5 per 2026-10-07 user prompt (the prompt is the spec;
no durable task-spec file was created)

## Baseline and worktree

Started at `fb9d3bb`, `git status` clean. Device `R5CN70E2SGV` (reports as SM-G986W,
Android 13) attached. Nothing committed, staged, or pushed; all work uncommitted in the
tree listed below.

## Completed and touched paths

`feature/home/src/main/.../feature/home/EditShortcutMenu.kt` (only production file):

- Slice 1 — pure sizing policy: added `EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS = 5.5`
  (half row hints scroll), `editShortcutMenuRowCapPx` / `editShortcutMenuOneRowPx`,
  `editShortcutMenuSideSpacePx` (+ `PixelRect` overload) returning
  `max(spaceAbove, spaceBelow)` clamped ≥ 0, and reworked `editShortcutMenuCappedHeightPx`
  to `(natural, rowCap, sideSpace, oneRow)` = `min(...).coerceAtLeast(oneRow)`.
- Slice 2 — `editShortcutMenuFrame` tries above-avoid then below-avoid, else a
  roomier-side fallback with horizontal clamping that can no longer land on the tile;
  an uncapped request taller than the roomier side shrinks to that height (menu scrolls
  the rest). The oversized-vs-viewport throw contract is unchanged. No old frame test
  asserted clamp overlap: `bottomRightCorner_clampsIntoViewport` and
  `rightEdgeTile_clampsAboveIntoViewport` are numerically identical under the new
  fallback, so no old frame test was modified — only the two viewport-cap tests whose
  contract was replaced.
- Handle overhangs (user decision 2, confirmed in `HomeGridOverlay.kt:65-119` +
  `EditControlPlacement.kt:49-63`): the 48dp resize Box is center-pinned on the
  bottom-right corner (spills ~half outside right/bottom on every selected tile); the
  48dp remove button is center-pinned on the top-right corner for 1×1/1×2/2×1 (spills
  right/top) and centered inside larger spans. New pure `editShortcutMenuOverhangs`
  span policy feeds both side-space and frame, so the menu clears the handles too.
- Slice 3 — composable computes sideSpace + capped height, `needsScroll` is now
  `natural > cappedHeight` (was `> viewport.height`); `menuScroll` confirmed already
  keyed on `tile.itemId` (no change). Geometry stays in `EditShortcutMenu.kt` — cohesive
  with the composable, no new file/module/edge.
- Slice 5 — class KDoc "safe viewport" + geometry bullets updated to the new cap rule.

`feature/home/src/test/.../EditShortcutMenuTest.kt`: 18 new/updated tests (row cap 5.5,
3-shortcut passthrough, 40-shortcut row cap, top/bottom side space, overhang reserve,
zero clamp, overload parity, one-row floor, Int extremes, rejection contracts, overhang
span policy, first/last-row + wide-span + scrolled-viewport non-overlap property tests,
fallback-shrink contract).

`feature/home/src/androidTest/.../EditShortcutMenuComposeTest.kt`: new
`manyEntries_menuNeverOverlapsTile_handlesStayHitTestableAndLastRowScrolls` (30 entries:
menu disjoint from tile + both 48dp handle boxes, last row `performScrollTo`-reachable,
resize-handle tap arms while the menu stays up — a covering menu would swallow it).

## Decisions

- Row cap 5.5: accepted. ~316dp max still leaves a fitting side above/below a 1×1 tile on
  a normal phone viewport; the half row signals scroll.
- `function_audit` flagged the composable at 118 lines after wiring; extracted the
  span→overhang mapping into pure `editShortcutMenuOverhangs` (responsibility split, now
  directly unit-tested) instead of shrinking code for line count.
- `module_graph.py`: no new edges. `compose_audit.py`: only pre-existing comment-word
  hits elsewhere.

## Validation

- `./gradlew :feature:home:testDebugUnitTest`: **557 tests, 0 failures**.
- `./gradlew test` (all modules): **1401 tests, 0 failures**
  (app 151, designsystem 155, grid 130, model 28, database 19, repository 27, allapps 80,
  home 555-at-the-time, feed 159, launcherapps 66, shortcuts 31).
- `./gradlew assembleDebug`: PASS. `./gradlew lint`: PASS (exit 0).
- `:feature:home:connectedDebugAndroidTest` full module on R5CN70E2SGV: **51/51 pass**
  (5 classes). Note: CURRENT.md quoted a stale "82 tests" baseline from the Phase-6 era;
  the module now contains exactly these 5 classes / 51 tests — 0 failures either way.
- Targeted re-run of `EditShortcutMenuComposeTest` after the overhang-extraction refactor:
  **18/18 pass** on R5CN70E2SGV.
- `repo_map.py` / `module_graph.py` / `function_audit.py` / `compose_audit.py`: run,
  no action (see above).
- Slice 4 problem-tile manual pass (top row, bottom row, scrolled page, landscape,
  resize with menu open): NOT RUN — the failing tile and its shortcut data live in the
  user's launcher state, which this session cannot identify or safely blind-drive via
  adb. Automated substitute on the same device: 30-entry menu + handle-tap coverage
  above. Suggested manual steps: (1) long-press the failing tile → menu shows ≤ ~5.5
  rows and scrolls; (2) with menu open, tap the ↘ handle → arms, tap again/resize works;
  (3) repeat on a top-row tile (menu flips below), bottom-row tile (menu above), after
  scrolling the page, and in landscape if supported.

## Remaining and next action

User (or a device session with the problem tile visible): run the Slice 4 manual pass
above on R5CN70E2SGV. Exit: resize starts with the menu open on the problem tile, or a
fix slice is opened with the observed geometry.
