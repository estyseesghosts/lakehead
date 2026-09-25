# D4 — Basic move editing

Status: open
Phase: D / 1C persisted Home
Owners: `:feature:home` (transient move-editing state, gesture presentation, orchestration); thin `:app` wiring; consumes the existing D2 `move` operation and `:core:grid` legality (as generalized by B4)

## Objective and bounded outcome

Add long-press-to-move editing for persisted `APP` placements on exactly one explicitly selected `HomeId`. The user long-presses a tile to enter move mode, drags (or otherwise designates) a candidate position, and lifts to commit or cancels to restore. Commit calls the existing `HomePlacementRepository.move(homeId, itemId, position, columns, rows)` off the UI thread with caller-supplied finite bounds; the grid result passes through unchanged. This slice adds no new persistence, schema, catalogue, or grid policy.

Depends on B4: move targets with generic spans must validate by bounds + collision, not by the old allowlist.

## State and interaction contract

- Transient, feature-owned move-editing state only: active `ItemId` (or none), original position, candidate position, per-candidate validity for display, and commit/cancel outcomes. Scroll position stays transient and per-Home as in D3. Nothing editing-related enters Room, `:core:model`, or the repository contract.
- Enter: long-press on an available tile enters move mode for that item; unavailable tiles are visible but not movable. No long-press behavior is added to overlays, All Apps rows, or unavailable tiles.
- Candidate: while in move mode, pointer movement designates a candidate cell; validity display derives from grid legality (bounds/collision) without writing. Crossing viewport edges may scroll; scrolling never rewrites persisted coordinates.
- Commit: on release over a candidate, invoke D2 `move` once and surface its explicit outcome: `Accepted` persists only the target's position; `Rejected` (with its grid reason) writes nothing; `NotFound`/`WrongHome` write nothing and exit move mode honestly.
- Cancel: system Back, outside-tap outside the dragged tile per the defined overlay policy, or an explicit cancel affordance aborts move mode with zero writes and restores the original position display.
- Post-commit the canvas reloads/reconciles that Home only; unrelated placements keep position/span/availability/order; holes are preserved; no compaction, sorting, or repacking ever occurs.
- Accessibility: move mode announces itself (label/role/state), dragged tile exposes its coordinates, targets meet at least 48dp, focus order stays coherent between canvas and any cancel/commit affordance, and a keyboard/d-pad alternative plus reduced-motion behavior exist. Correctness never depends on animation.

## Ownership and dependency direction

- `:feature:home` owns gesture presentation, transient move state, orchestration of the single D2 `move` call, and outcome display. It performs no catalogue/platform lookup, no icon decode, no database access from composables, and no grid-policy duplication.
- `:core:grid` remains the sole legality owner; `:data:repository` remains the sole write owner; `:platform:launcherapps` remains the metadata/launch owner; `:app` only wires the chosen `HomeId`, bounds, and launch contracts. No feature-to-feature dependencies; no new module.

## Acceptance criteria

- Long-press on an available tile enters move mode; long-press elsewhere does nothing.
- Commit to a free in-bounds target persists only that tile's new position; unrelated rows/order/holes/availability are byte-identical before/after (repository snapshot style).
- Commit to a colliding or out-of-bounds target surfaces the exact grid rejection and writes nothing.
- Cancel (Back/outside-tap/affordance) writes nothing and restores the original display.
- Unknown-Home/item and wrong-Home requests fail explicitly with no writes and exit move mode.
- Restart after an accepted move shows the moved tile at its new logical coordinates with everything else unchanged.
- No resize, remove, provisioning, Home selection, shortcut/appearance, hit-target, folder, or All Apps behavior is added or claimed.

## Required tests

- Feature state-machine unit tests: enter (available vs unavailable vs empty space), candidate validity transitions, commit-accepted, commit-rejected (collision and out-of-bounds keep full snapshot), cancel paths, stale-request handling (an older commit response cannot overwrite newer state).
- Orchestration tests: exactly one D2 `move` call per commit with the explicit `HomeId`/`ItemId`/position/bounds; outcomes (`Accepted`/`Rejected`/`NotFound`/`WrongHome`) map to the correct display state with zero extra writes.
- Compose/UI tests: long-press enters move mode, invalid-target feedback, cancel restores, 48dp targets, labels/roles, traversal order, reduced-motion path. No database, catalogue, or icon work inside composition.
- `:app` wiring test: chosen `HomeId` and finite bounds pass through unchanged; no business logic in the Activity.
- Device proof (SM-G986W): install, select ProtoLaunch as HOME if needed, long-press a seeded tile, move to a free cell (persists across restart), attempt a colliding move (rejected, nothing shifts), cancel a move (restores). Record model/API/serial, install result, and observed behavior; installation alone is not evidence.

## Explicitly deferred

- D5 resize UI, D6 remove/reset, Home provisioning/selection, catalogue-to-placement sync, `Flow` observation.
- Tile content contracts from `docs/grid.md` (hit-target separation, symbol placement rule, `TileColorRole` theming, configured shortcut slots) — later tile slice; D4 reuses current tile rendering as-is.
- Folders, widgets, shortcuts, expanded tiles, search, messages, feed.
- Auto-compaction, packing, or coordinate rewriting on any path.

## Validation

Focused tests first, then (record exact results; `NO-SOURCE` distinct from pass):

```text
./gradlew :feature:home:testDebugUnitTest
./gradlew :data:repository:testDebugUnitTest
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Adapt focused task names only if the module build config requires it. Manual TalkBack speech, focus order, large-font, and reduced-motion observations are **unverified** unless actually run.

## Narrow exit gate

D4 is complete when one Home's available tiles can be long-press-moved to free targets (persisted, restart-stable), invalid moves and cancels write nothing and shift nothing, holes and unrelated tiles are preserved, focused + full validation plus recorded device proof pass, and dependency/ownership constraints hold. D5, D6, and all deferred behavior remain open.
