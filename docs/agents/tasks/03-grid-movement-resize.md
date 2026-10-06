# Combined implementation plan

> **SUPERSEDED — not an instruction to implement.** Replaced by `../../lakehead-grid-repair-plan.md` (user-directed 2026-10-05). Its Phase 1 is complete; the active objective is tracked in `../../../state-logs/CURRENT.md`.
>
> Nothing below may be used to justify new work. The clamp/OOB behavior (M1), the eight-step MOVE generation budget (M2), structural re-freezing, signed origins and row-window movement frames are **retired contracts**; see `../../../design.md` "Frozen Home grid contract" and §3 of the repair plan. The one idea that survives is route-once routing: after slop a drag is MOVE or RESIZE until release or cancel.
>
> Do not run the phase gates, slice tables or contract decisions below again. They describe the superseded patch-by-patch hardening path.

## Objective

Deliver a coordinated `:feature:home` change that makes MOVE mapping stable across scrolling, bounds autoscroll per drag, and preserves the specified resize-arm behavior across gesture routing and selection transfer. Validate the combined behavior with the requested regression matrix and device-level pointer interaction.

**Primary owner:** `:feature:home`. MOVE frame and gesture state remain transient feature state; grid placement rules remain owned by `:core:grid`.

## Diagnosis

The MOVE mapping, autoscroll ticker, and render-time scroll origin currently risk using inconsistent coordinate baselines. The ticker also needs an explicit per-drag work budget. Separately, resize intent is sampled too early and can be lost or incorrectly routed when gesture ownership transfers. These changes share source areas and should be implemented in dependency order, then covered by a combined edge-case matrix.

## Carried-forward scope (verbatim)

- **M1:** feature-owned immutable MoveCoordinateFrame (frozen scroll baseline+originX+originYPx+rowOrigin+strides+columns+span+legality rows+grab companion); mapper takes frame+pointer+current scroll; store beside MOVE state 303-372; remove tickOriginYLive from mapping 622-639/829-847 (render-only); atomic re-freeze on structural change only; single mapping path events+ticker as baseline+live delta; span-aware clamp with OOB if unfittable; P1 conflict: D4 requires OOB surfacing vs clamp — decision required before implementation.
- **M2:** 8 successful one-row steps per MOVE drag cap (both directions, exits included; count actual ScrollState change; bound no-op free); constant HomeEditFrontier 21-35 + predicate 240-244; ticker 609-820 local count, remap preserved 724-807; gesture generation 354-372/1535-1576 keying 640; exit cancels dwell not budget; lift/cancel stops; cap stops ticker, drag active, release commits. Tests thresholds + real-pointer.
- **R1:** keep two-gesture (tap arms, armed-at-slop drag resizes, unarmed handle NONE); remove armedAtDown 1177-1182, sample snapshotResizeArmed AFTER slop 1201-1230 before 1237, route 1318-1323; press-anchored geometry unchanged; route-once; comment updates; primitive tap-only unchanged.
- **R2:** remove disarmResize 1336-1340; transfer 1155-1183/1237-1257/1315-1323/2016-2031 routes armed-handle=>RESIZE (re-arm transferred selection), unarmed=>NONE, body=>MOVE; release 1452-1528 keeps onResizeTile exclusive, replace Active(MOVE) rebuild with idle session preserving Editing+arm; KDoc updates.
- **T:** matrix spans 1x1/2x1/1x2/2x2/4x2 x top/mid/bottom x left/right x shallow/deep + control; files MoveDragMathTest, HomeEditSessionTest, HomeEditFrontierTest, HomePointerLoopRegressionTest, HomeInteractionControllerTest, MoveResizeRoutingTest, HomeCanvasTest; six-col fixtures.

## Contract decisions required **before implementation**

1. **Resolve the M1 P1 conflict.** Decide the precedence between D4's OOB-surfacing requirement and clamping. The supplied "span-aware clamp with OOB if unfittable" is a candidate contract, not an implicit resolution. Specify what the mapper returns for a span that fits after clamping versus one that cannot fit.
2. **Define the structural-change boundary for re-freezing.** Identify which frame inputs count as structural changes and how a re-freeze preserves the active drag's pointer/grab relationship. Ordinary live scroll updates must use the frozen baseline plus live delta, not trigger a structural re-freeze.
3. **Confirm the M2 budget semantics.** A successful step is counted only when `ScrollState` actually changes; no-op attempts are free. Confirm that the eight-step budget is per MOVE drag, shared across exits/re-entry and both directions, and that reaching the cap stops the ticker without ending the drag.
4. **Confirm the resize transition contract.** Keep tap-to-arm separate from slop-time drag routing; transferred selection must retain the specified armed/unarmed routing, and release must preserve editing and arm state as specified.

**Gate:** Do not start implementation until the M1 conflict and the structural re-freeze contract are settled.

## PHASE 1 — Contracts

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Freeze MOVE mapping and re-freeze contract | `:feature:home` contract/KDoc locations; `docs/design.md` only if the resolution changes a stable documented contract | Document the chosen OOB/clamp behavior, frame inputs, structural-change predicate, and baseline/live-scroll relationship. | Review the contract against M1 and the P1 decision; no code changes until unambiguous. |
| Freeze frontier and budget contract | `:feature:home` frontier/ticker source locations and relevant test contracts | Confirm eight successful one-row changes per drag, exit/re-entry behavior, no-op accounting, lift/cancel, and cap behavior. | Check against M2 acceptance and identify threshold cases before implementation. |
| Freeze resize routing and transfer contract | `:feature:home` gesture-routing and release source locations; relevant KDocs/comments | Confirm post-slop arm sampling, route-once behavior, transfer routes, and release-state preservation. | Check the R1/R2 transitions together before touching their shared routing code. |

## PHASE 2 — M1: Stable MOVE coordinate frame

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Add the frame and mapper contract | `:feature:home` — MOVE state source at lines 303–372 and the source containing the MOVE mapper; `MoveDragMathTest` | Add the feature-owned immutable `MoveCoordinateFrame` with frozen scroll baseline, `originX`, `originYPx`, `rowOrigin`, strides, columns, span, legality rows, and grab companion. The mapper accepts frame, pointer, and current scroll. | Unit tests cover deterministic mapping from the frozen frame plus live scroll delta, including the settled OOB/clamp contract. |
| Use one mapping path for events and ticker | `:feature:home` — mapping locations at lines 622–639 and 829–847, plus the event/ticker callers | Store the frame beside MOVE state. Remove `tickOriginYLive` from mapping and keep it render-only. Both event and ticker paths use the same baseline-plus-live-delta mapping path. | Regression tests prove event and ticker mapping agree for the same frame, pointer, and current scroll. |
| Re-freeze only for structural change | `:feature:home` — MOVE state/frame update source and `HomeEditSessionTest` | Re-freeze atomically on the agreed structural change only; ordinary scrolling does not replace the frozen frame. Apply the agreed span-aware clamp and OOB behavior. | Test structural changes, non-structural scroll changes, and span edges, including an unfittable span. |

## PHASE 3 — M2: Bounded MOVE autoscroll

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Define the frontier | `:feature:home` — `HomeEditFrontier` constant at lines 21–35 and predicate at 240–244; `HomeEditFrontierTest` | Encode the agreed frontier predicate and the eight-successful-step limit without introducing a second placement rule. | Test thresholds, both directions, and the specified frontier cases. |
| Bound ticker work and preserve remapping | `:feature:home` — ticker at lines 609–820 and remap at 724–807; `HomePointerLoopRegressionTest` | Keep the counter local to the drag; count actual `ScrollState` changes, not attempts. Exits cancel dwell but retain budget. At the cap, stop the ticker while keeping the drag active; release commits. | Test successful steps, no-op attempts, exit/re-entry, generation changes, cap behavior, and release/cancel paths. Include real-pointer regression coverage. |
| Preserve lifecycle ownership | `:feature:home` — gesture-generation locations at lines 354–372, 1535–1576, and keying at 640 | Lift/cancel stops the ticker; gesture generation prevents stale work; no-op work remains bounded. | Run the relevant pointer-loop tests and confirm stale ticker work cannot act on a later gesture. |

## PHASE 4 — R1: Slop-time resize routing

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Sample resize intent after slop | `:feature:home` — gesture source at lines 1177–1182, 1201–1230, and 1318–1323; `HomeInteractionControllerTest` | Remove `armedAtDown`; sample `snapshotResizeArmed` after slop and before the route decision. An armed handle drag resizes; an unarmed handle drag routes to NONE. | Test tap-to-arm and armed/unarmed handle drags across the slop boundary. |
| Preserve existing gesture invariants | `:feature:home` — same gesture source and `MoveResizeRoutingTest` | Keep press-anchored geometry unchanged, route once, update comments, and leave primitive tap-only behavior unchanged. | Regression tests verify the route is emitted once and primitive tap-only behavior is unaffected. |

## PHASE 5 — R2: Transfer and release routing

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Route transferred selection by arm state | `:feature:home` — transfer locations at lines 1155–1183, 1237–1257, 1315–1323, and 2016–2031; `MoveResizeRoutingTest` | Remove `disarmResize`. Transferred armed handle routes to RESIZE and re-arms the transferred selection; unarmed handle routes to NONE; body routes to MOVE. | Test each transfer route for both armed and unarmed states. |
| Preserve editing and arm state on release | `:feature:home` — release source at lines 1452–1528; `HomeEditSessionTest`, `HomeInteractionControllerTest`; relevant KDocs | Keep `onResizeTile` exclusive. Replace the `Active(MOVE)` rebuild with an idle session that preserves Editing and arm state. Update KDocs. | Test release and transfer sequences for retained edit session and arm state, plus cancel behavior. |

## PHASE 6 — T: Regression matrix and device validation

| Slice | Files / owner | Acceptance | Validation |
|---|---|---|---|
| Complete the regression matrix | `:feature:home` tests: `MoveDragMathTest`, `HomeEditSessionTest`, `HomeEditFrontierTest`, `HomePointerLoopRegressionTest`, `HomeInteractionControllerTest`, `MoveResizeRoutingTest`, and `HomeCanvasTest` | Cover spans 1x1, 2x1, 1x2, 2x2, and 4x2 across top/mid/bottom, left/right, and shallow/deep cases, plus a control. Use six-column fixtures. Distribute cases across the named tests without weakening the individual M1/M2/R1/R2 invariants. | Run the relevant `:feature:home` tests and the full `./gradlew test`; verify the matrix includes the real-pointer regression cases. |
| Verify on device | `:feature:home` behavior in the integrated app; device-level validation with `:app` as integration owner | Verify pointer-driven MOVE/autoscroll and resize routing on device, including cap/release behavior and the six-column layout. Record device/OS and observed outcomes; do not infer device behavior from unit tests. | Use a connected-device/instrumented or manual pointer run. For the implementation slice, also run `./gradlew assembleDebug`, `./gradlew lint`, and the required repository audits; record exact outcomes. |

## Exclusions

- No persistence-model changes: the coordinate frame, autoscroll counter, and gesture state are transient feature state.
- No grid-placement or packing implementation in `:feature:home`; placement legality remains owned by `:core:grid`.
- No rendering-derived coordinate mapping; render-only `tickOriginYLive` must not re-enter the mapper.
- No changes to platform catalogue, launcher integration, or unrelated modules/features.
- No change to primitive tap-only behavior, and no broad gesture redesign beyond the specified R1/R2 routes.
- No feature expansion into folders, widgets, search, notifications, or other roadmap work.

## Parallel-work optimization

- **Serialize M1 then M2:** both touch MOVE state, mapping, and ticker behavior. Finish the shared mapping contract before changing ticker budget/remapping behavior.
- **Serialize R1 then R2:** both touch gesture routing and selection/session transitions. Land slop-time sampling before transfer/release preservation.
- **Serialize `HomeCanvasTest` edits:** keep one owner for its shared six-column fixtures and matrix assertions, after the behavior contracts settle.
- **Parallelize across files after Phase 1:** independent test-file work can proceed in parallel with a single writer per test file—for example, MOVE math/frontier coverage separate from resize-routing coverage. Keep implementation edits to shared source files serialized as above, and integrate all tests before device validation.
