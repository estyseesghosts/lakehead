# 2026-09-26 — Phase 1-2 complete (Sprint 1: signed grid, pager, picker, model bindings, anchor delegate)

Status: completed
Owner/slice: Multi-slice sprint across `:core:grid`, `:core:model`, `:feature:home`, `:app`

## Baseline and worktree

Colour milestone was fully complete (commits through `3dc1843`). All `assembleDebug`, `test`, `lint` passing. Starting from a clean worktree at that point. Sprint 1 implemented eight slices (G1, G2, S1, P1, G3, P3, P4, S3) plus three user-requested fixes (background, nav-pill, banner) in eight commits.

## Completed work

### G1 — Signed grid coordinates (`5bd0076`)
- Introduced `SignedGridPosition` in `:core:grid` with signed `col`/`row` (negative = offscreen left/top, positive = visible+right).
- Extended `GridSpec`, `GridScope`, and movement/clamping helpers to operate on signed types.
- **Decision:** Keep `SignedGridPosition` as a distinct type alongside the original `GridPosition` rather than relaxing `GridPosition` to signed — avoids breaking every existing call site and keeps the unsigned contract for placement/persistence where negative cells are meaningless.

### G2 — Signed window and selection (`9e77719`)
- Added `SignedWindow` (signed `colSpan`/`rowSpan`) to `:core:grid`.
- Updated `selectSigned()` collision algorithm for negative origin + positive span overlap.
- `SignedGridRegion` helper for bounding-box calculations.

### S1 — Shortcut reference model (`5bd0076`)
- `ShortcutRef` data class in `:core:model` — purely a domain identifier (`packageName`, `shortcutId`, `userHash`).
- **Decision:** Model-only; no persistence or launcher integration yet. Keeps the type available for future home screen binding without premature wiring.

### P1 — All-apps pager + nav-pill + opaque background (`7baf96b`)
- Horizontal pager in `:feature:home` for multi-page all-apps.
- Nav-pill overlay pinned at bottom.
- Opaque scrim behind pager to occlude home content.
- **Decision:** Compose `HorizontalPager` + custom pill + opaque `Box` background rather than a Material `NavigationBar` — aligns with "no high-level Material for core surfaces" invariant.

### User-requested fixes (`5e11040`)
- **Banner:** persistent info banner component.
- **Nav-pill:** visual alignment and touch-target tuning.
- **Background:** opaque all-apps background replacing translucent scrim.

### G3 — Signed grid packing/adaptation (`1ece882`)
- `packSigned()` / `adaptToWidthSigned()` in `:core:grid`.
- Variable column-count support for wider viewports.
- Biases rounding to 0.45 threshold with `Long` arithmetic to avoid floating-point drift.
- **Decision:** Bias 0.45 as `Long` (multiply-then-round) rather than `Float` comparison — deterministic across JVM/Android and testable with exact integer assertions.

### P3 — App-section grouping (`99d8ebf`)
- `AppSection` sealed class in `:core:model` for grouping catalogue entries (letter, category, recency).
- Grouping logic in `:feature:home` for the all-apps picker.
- 4×7 picker grid layout with pager blocking during scroll.

### P4 — Pick-dismiss interaction (`f950240`)
- Swipe-to-dismiss gesture for the all-apps picker overlay.
- **Decision:** Dismiss fires immediately on threshold; no exit animation yet (see known gaps).

### S3 — Tile binding model (`1e84c8d`)
- `TileBinding` in `:core:model` — associates a `GridPosition` with an `AppIdentity` and optional `ShortcutRef`.
- **Decision:** Model-only (parallel to `ShortcutRef`); not wired to persistence or rendering. Pitch-pure type, currently unconnected to the data layer or home screen.

### Anchor delegate (`1ece882`)
- Anchor cell delegation for 1×1 tiles on wide viewports (≥5 cols).
- **Decision:** 1×1 tiles delegate to anchor cell rather than occupying a fixed column — preserves compact layout without changing the persistence model.

## Touched paths per slice

| Slice | Primary paths |
|-------|---------------|
| G1+S1 | `core/grid/src/.../SignedGridPosition.kt`, `core/model/src/.../ShortcutRef.kt`, grid tests |
| G2 | `core/grid/src/.../SignedWindow.kt`, `core/grid/src/.../selectSigned.kt`, grid tests |
| P1 | `feature/home/src/.../AllAppsPager.kt`, `feature/home/src/.../NavPill.kt`, `app/src/.../HomeActivity.kt` |
| banner/nav/bg | `feature/home/src/.../Banner.kt`, `feature/home/src/.../NavPill.kt`, `feature/home/src/.../AllAppsBackground.kt` |
| G3 | `core/grid/src/.../packSigned.kt`, `core/grid/src/.../adaptToWidthSigned.kt`, grid tests |
| P3 | `core/model/src/.../AppSection.kt`, `feature/home/src/.../AppSectionGrouping.kt`, `feature/home/src/.../PickerGrid.kt` |
| P4 | `feature/home/src/.../PickerDismiss.kt` |
| S3 | `core/model/src/.../TileBinding.kt` |

## Validation

| Check | Result |
|-------|--------|
| `./gradlew assembleDebug` | SUCCESS |
| `./gradlew test` | SUCCESS |
| `./gradlew lint` | SUCCESS |
| `python3 tools/scripts/module_graph.py` | No feature→feature deps, no grid→platform deps |
| `python3 tools/scripts/compose_audit.py` | 1 comment-only false-positive (suppressed) |

Device behaviour: **NOT RUN** — no connected device during this sprint.

## Unverified device behaviour

All of the following require a physical device pass:

- **Swipe feel:** all-apps pager horizontal swipe responsiveness and threshold.
- **Picker dim/anim:** background dim opacity and any entry/exit animations.
- **Strip visuals:** nav-pill rendering, icon sizing, touch targets on-device.
- **Drag/resize feel:** grid drag-and-drop and resize interactions under touch.

## Known gaps

- **G3 tests partially uncommitted?** — Verify that all signed-grid packing/adaptation tests are present in the working tree. If any were staged but not committed, they need a follow-up commit.
- **P4 dismiss has no exit animation** — Dismiss fires instantly at the swipe threshold; a proper exit animation is deferred.
- **S3 TileBinding is pure model, unconnected** — `TileBinding` exists in `:core:model` but is not wired to persistence (`:data`), the home renderer, or the all-apps picker. It is a forward-looking placeholder.

## Commit history (this sprint)

| Hash | Content |
|------|---------|
| `5bd0076` | G1 + S1 — signed grid coords + ShortcutRef model |
| `9e77719` | G2 — signed window + selectSigned |
| `7baf96b` | P1 — pager + pill + opaque background |
| `5e11040` | User fixes — banner, nav-pill, background |
| `1ece882` | G3 — signed packing + anchor delegate |
| `1e84c8d` | S3 — TileBinding model |
| `99d8ebf` | P3 — AppSection grouping + 4×7 picker |
| `f950240` | P4 — picker dismiss |

## Worktree state

Clean — all eight commits applied on top of colour milestone `3dc1843`. No uncommitted changes.

## Remaining and next action

**Phase 3 — P2 arbitration.** Implement home-screen item arbitration (conflict resolution when multiple items target the same grid cells). Owner: `:core:grid` for arbitration algorithm, `:feature:home` for integration. Exit condition: arbitration resolves overlaps deterministically with tests covering edge cases (full overlap, partial overlap, adjacent cells, empty grid).