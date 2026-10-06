# Handoff — A5 unpin placement (01.md slice A5)

Date: 2026-09-27. Owner: five (implementer). Scope: `docs/agents/tasks/01.md` slice A5 only.

## Completed work (factual)

- New `feature/home/src/main/java/me/foxtails/lakehead/feature/home/EditControlPlacement.kt`
  (pure, framework-free: no composables, no Android types; `IntOffset` value type only,
  same as existing `cornerControlOffset`):
  - `RemoveControlPlacement { TOP_RIGHT, CENTER }`.
  - `removeControlPlacement(columns, rows)`: `1×1, 1×2, 2×1 → TOP_RIGHT`, every other
    positive span → `CENTER` (incl. `2×2`, `3×1`, `1×3`). Non-positive spans throw.
  - `removeControlOffset(...)` overloads for `SignedPixelFrame` and `PixelRect`:
    `TOP_RIGHT` delegates to `cornerControlOffset(..., ControlCorner.TOP_RIGHT)` (center
    pinned on the tile's top-right corner); `CENTER` mid-aligns the control in the tile
    footprint. Signed tops stay rebased (no clamping).
- `feature/home/.../HomeCanvasGrid.kt`: the remove overlay now derives placement from the
  **live preview span** (`removeControlPlacement(previewSpan.columns, previewSpan.rows)`)
  and positions via `removeControlOffset(tileBounds, buttonPx, placement)`. `tileBounds`
  is the existing live frame (preview span + active origin), so a resize crossing the
  1×1→2×2 boundary re-anchors mid-drag, and move keeps the button on the tile. Resize
  handle untouched (always bottom-right). KDoc corner-control paragraph updated.
  `RemoveButton.kt` + `TileBounds.kt` untouched (read-only per slice).
- Tests:
  - New `EditControlPlacementTest` (unit, 11 tests): all small spans → TOP_RIGHT, boundary
    + larger spans (2×2, 3×1, 1×3, 2×3, 3×2, 4×2, 3×3, 6×4) → CENTER, invalid spans throw,
    TOP_RIGHT == corner contract coords, CENTER mid-align coords (both overloads, signed
    top), move-tracking (shifted origin shifts offset), resize-boundary flip, non-positive
    control throws.
  - `CornerControlContractTest` +1 test: small spans keep the top-right corner contract,
    2×2 centers on the tile middle, centered remove stays outside the handle routing rect.
  - `HomeCanvasTest` (androidTest) +2 semantics tests: 1×1 remove center == tile
    (right, top) corner; 2×2 remove center == tile center; both tap → onRemove only.
  - `HomeCanvasTest` harness fix (test-only): all 7 content roots now compose via new
    `setThemedContent` helper wrapping in `LauncherTheme`. Required because `AppTile`
    now requires `LocalLauncherDimensions` (production provides it at
    `MainActivity.kt:221`); without it every composed test crashes with
    "No LauncherDimensions provided". Theme is colour-only + dimensions; no assertion
    semantics change.

## Touched paths

- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/EditControlPlacement.kt` (new)
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvasGrid.kt` (remove block + KDoc)
- `feature/home/src/test/java/me/foxtails/lakehead/feature/home/EditControlPlacementTest.kt` (new)
- `feature/home/src/test/java/me/foxtails/lakehead/feature/home/CornerControlContractTest.kt` (+1 test)
- `feature/home/src/androidTest/java/me/foxtails/lakehead/feature/home/HomeCanvasTest.kt` (+2 tests, theme harness)

## Decisions and reasons

- `2×2 → CENTER`: slice says "cols>2||rows>2→CENTER (2×2 centered)" — the parenthetical
  wins, so the predicate is "TOP_RIGHT iff 1×1/1×2/2×1, else CENTER" (covers 3×1/1×3 too).
- `IntOffset` in the "pure" file: matches existing `cornerControlOffset` precedent; unit-testable
  on JVM, no Compose runtime or Android types.
- No `EditShortcutMenuComposeTest` changes: its `LauncherDimensions` crash is the same
  harness gap but that file belongs to B4/menu work — left for its owner (see blockers).
- Device `screen_off_timeout` set to 1800000 via adb during diagnosis (was unknown default);
  disclose to device owner; revert as desired.

## Exact validation performed with outcomes

- `./gradlew :feature:home:testDebugUnitTest` — BUILD SUCCESSFUL. Home unit total 467 tests,
  0 failures/errors/skipped (incl. new EditControlPlacementTest 11/11, CornerControlContractTest 8/8).
- `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew lint` — BUILD SUCCESSFUL (re-run after final test edits).
- `./gradlew :feature:home:assembleDebugAndroidTest` — BUILD SUCCESSFUL (androidTest compiles).
- `./gradlew installDebug` — BUILD SUCCESSFUL, installed on SM-G986W (R5CN70E2SGV).
- `repo_map / module_graph / function_audit / compose_audit` — ran; no new flags, no new
  module edges (`:feature:home → :core:*` unchanged; new file imports only value types).
- `:feature:home:connectedDebugAndroidTest` (HomeCanvasTest filter) — **NOT PASSING, environmental**:
  all composed tests fail with "No compose hierarchies found" because the device sits at the
  secure-lock **Bouncer** (`mCurrentFocus=Window{...Bouncer}`); every test activity is STOPPED
  ~80 ms after resume, before assertions run. Two non-composing tests pass. Full-suite run
  additionally showed the pre-existing `EditShortcutMenuComposeTest` LauncherDimensions crash.

## Known failures / incomplete work

1. **Device locked (blocker for all on-device proof):** SM-G986W shows the Bouncer; `wm
   dismiss-keyguard` cannot clear a secure lock via adb. All connected-test and manual
   placement verification (button follows tile, corner→center flip on device) is blocked
   until the device owner unlocks the phone. No production code is implicated.
2. **Pre-existing `EditShortcutMenuComposeTest` harness gap** (same LauncherDimensions cause,
   different file, B4 owner): crashes process before `HomeCanvasTest` in full-suite runs;
   needs the same `LauncherTheme` wrap or an owner fix.

## Worktree/commit state

- Nothing committed, staged, stashed, or reset (per no-commit rule). Untracked new files:
  `EditControlPlacement.kt`, `EditControlPlacementTest.kt`. Modified: `HomeCanvasGrid.kt`,
  `CornerControlContractTest.kt`, `HomeCanvasTest.kt`. Other working-tree modifications
  (feed, MainActivity, agent docs) are concurrent-wave work, untouched.
- `state-logs/CURRENT.md` left untouched for the orchestrator (still points at A2-S1 device
  verification as the active objective).

## Next action (exit-conditioned)

Device owner unlocks SM-G986W (keep screen on), then run
`./gradlew :feature:home:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.feature.home.HomeCanvasTest`
and confirm 30/30 pass (28 existing + 2 new placement semantics); exit when green or a
concrete in-app failure is filed as a bounded follow-up. Manual spot-check: long-press a
1×1 tile (× at top-right corner) vs a 2×2 tile (× centered), resize across the boundary,
confirm the × re-anchors mid-drag.
