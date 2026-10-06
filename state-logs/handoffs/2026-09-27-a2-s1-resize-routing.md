# 2026-09-27 — A2-S1 tap-vs-drag resize routing (tap-to-arm)
Status: completed; device-interaction pending
Owner/slice: `feature:home` + `core:designsystem`, `docs/agents/tasks/01.md` A2-S1

## Baseline and worktree
Started from a worktree with extensive concurrent changes (other agents' modified
`.opencode/agents/*`, `MainActivity.kt`, `HomeEditSession.kt`, feed AIDL work, etc.).
Those were preserved untouched; this slice edited only the files listed below via
exact-match replacements. `HomeEditSession` internals, `MoveDragMath`/`ResizeDragMath`,
`GridRules`, persistence, feed/shortcuts/sorting/scroll-expansion and the viewport
interface were not touched.

## Completed and decisions
Tap-to-arm resize routing per the A2-S1 card (outline/tile-follow preview choice
deferred to S2; tile-follows preview retained, span fixed either way):

- `feature/home/.../HomeInteractionController.kt`: `Editing` gains transient
  `resizeArmed: Boolean = false`, owned by `selectedId`. New pure `armResize()`,
  `disarmResize()`, `toggleResizeArmed()` (no-ops in `Normal`/null selection).
  `selectOnTap` transfers + clears, `enterOnLongPress` retargets + clears,
  `unpinIfSelected` clears, all exits drop the arm with the selection. No
  persistence, no grid mutation.
- `feature/home/.../HomeCanvasGrid.kt`: drag handler snapshots
  `armedAtDown = snapshotResizeArmed(interactionAtDown, dragTile?.itemId)` once at
  down; past slop `routeDownDrag(arrowHit = startInHandle, armed = armedAtDown,
  hasTile = true)` routes once — armed arrow → RESIZE (gates `beginResize`),
  body → MOVE (span fixed from entry `originSpan`, clears arm via `disarmResize`),
  unarmed arrow → NONE (zero-write `cancel()` of the just-entered session, stays
  wiggle-editing, hold ends). No mid-gesture switch; pager-hold arbitration block
  unchanged. Below-slop arrow-taps are consumed by the handle's tap action
  (toggle, no write, no `enterSelectedDrag`); parent tap path stays a no-op.
  New pure `DownDragRoute`, `routeDownDrag()`, `snapshotResizeArmed()` at file
  bottom. KDoc updated to the armed contract.
- `core/designsystem/.../ResizeHandles.kt`: `ResizeHandle(label, armed,
  onToggleArmed, modifier)` — filled-primary vs outline 32dp circle, merged
  semantics `"Arm/Disarm resize for {label}"` + `"Resize armed/not armed"` state,
  tap-only `clickable(Role.Button)` when a toggle is supplied (no drag/slop
  detector; drags stay parent-owned, same precedent as `RemoveButton`).
- Grid handle overlay passes `armed` + `onToggleArmed` (guarded on selected-item
  match) and reuses one label for both corner controls. This overlay wiring is
  outside the card's listed line ranges but is the only way to deliver the
  required accessible arm/disarm without a competing detector.
- Tests: `HomeInteractionControllerTest` +17 (arm/disarm/toggle, transfer/retarget/
  unpin/exit clearing, equality incl. flag), new `MoveResizeRoutingTest` +15
  (route matrix incl. arrow-wins-over-body, ownership, snapshot stability),
  `CornerControlContractTest` +2 (48dp corner vs body vs remove-center disjointness,
  opposite-edge anchors). Move-span preservation already covered by existing
  `HomeEditSessionTest` (line ~578, untouched).

## Validation
- `./gradlew :feature:home:testDebugUnitTest` — SUCCESS, 431 tests, 0 failures
  (397 baseline + 34 new).
- `./gradlew :feature:home:assembleDebug` — SUCCESS.
- `./gradlew assembleDebug` — SUCCESS.
- `./gradlew installDebug` on SM-G986W (R5CN70E2SGV, Android 13) — SUCCESS
  (app-debug.apk + fixtures installed).
- Extra: `./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:lint
  :feature:home:lint` — SUCCESS.
- Each command ran in its own shell invocation. No `connectedAndroidTest` run.

## Remaining and next action
Device-interaction proof still outstanding: on the SM-G986W, enter edit
(long-press), tap the resize arrow (arm shows filled dot + "Resize armed"),
verify arrow-drag resizes, unarmed arrow-drag does nothing, body-drag moves with
fixed span and disarms, tap-transfer/back/empty-tap clear the arm, and TalkBack
announces Arm/Disarm actions. Next action: orchestrator runs that device pass
(or delegates a bounded gesture-verification slice); exit when each behavior is
confirmed or a concrete failure is filed as a follow-up. S2 placement
(move/resize commit separation, outline-vs-follows decision) is the next
implementation slice and must not start inside this routing change.
