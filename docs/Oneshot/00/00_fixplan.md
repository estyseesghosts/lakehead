# ProtoLaunch — `docs/00_fixing.md` Long-Form Fix Plan

> Planning only — no files edited this turn. Orchestrated via subagents. Five-column baseline per your 2026-09-26 directive. Any six-column assumption in `first_draft.md` / `b1` / `AGENTS.md §4` is recorded as `IGNORED-SIX-COLUMN` and not planned against.
> Sources: `zero` summaries of `00_fixing.md`, `AGENTS.md`, `architecture.md`, `design.md`, `state-logs/CURRENT.md`, `docs/agents/tasks/`, gesture/feed/shortcut/resize maps + `three` phased plans for gating / tile-rework / feed.

## 0. Where we are vs what `00_fixing.md` asks

`00_fixing.md` — *Launcher Gesture and Edit Mode Corrections*: too many overlapping gesture handlers; tile-shortcut swipes + page swipes interfere with edit mode. 7 sections + exit gates:

1. Restrict horizontal page swipe to bottom 33%, disable in edit, edit takes precedence, tile touch never becomes page swipe
2. Remove swipe-to-open shortcuts entirely
3. Dedicated resize control (1×1: keep top-left `(×)` unpin + add bottom-right drag handle; larger tiles same; obey grid legality)
4. Restore tile movement in edit (body=move, handle=resize, unpin=remove, tap-other=reselect; move≠resize; no accidental nav/shortcut/resize)
5. Shortcuts from edit mode in menu **above** selected tile (none → no menu; follows selection; dismiss on exit/move/resize without activating; basic visuals OK)
6. Priority: edit controls > move > selection > shortcut-menu > normal tile > page nav lowest; single deliberate ownership, no racing handlers
7. Replace left `FeedSurface` placeholder with real Launcher3-style provider host (Lawnfeed/Discover, Neo Feed); keep `Feed ↔ Tile ↔ Apps list`; isolate from grid gestures

Exit gates as stated: edit reliably selects/moves/resizes/unpins; shortcuts via selected tile; no shortcut swipes; page swipes bottom-third only + disabled in edit; dedicated handle; movement works; no competing handlers; feed hosts real providers.

Current state (`state-logs/CURRENT.md`): `Phase 3+4 — NEARLY COMPLETE. Interaction + Persistent Edit Core committed. W2+W4 pending commit.` Next op: commit W2+W4 worktree, device manual validation on SM-G986W, then Phase 5 readiness.

No blocking `CONFLICT` found. Sequencing note (not a blocker): `§4` names a post-Phase-4 move regression — plan sequences **after** Phase-4 closeout commit so W2 (`MoveDragMath`/`HomeGestureArbitration`) + W4 (unpin/arbitration) are preserved. Partial overlap noted: bottom-right `ResizeHandle` + `RemoveButton` + `isInHandle` + `removeButtonOffset` + `ACTIVE_RESIZE/ACTIVE_MOVE` arbitration already exist — slices below harden/remove/rewire, not greenfield.

Ownership contract carried throughout (per `AGENTS.md`/`architecture.md`/`design.md`):

- **State:** transient gesture in `feature:home`, page index in `app/LauncherShell`, legality/packing in `core:grid`, catalogue in `platform:launcherapps`, persisted placement in future `data:*`
- **Platform:** `platform:launcherapps` (today) + new `platform:feed` or `feedoverlay/` subpackage only when overlay code needs boundary + thin `platform:shortcuts` query only if discovery gap requires it
- **Grid:** `core:grid` sole legality owner — no bounds/collision duplication in feature
- **UI:** `feature:home` arrangement + `core:designsystem` pure primitives (`GestureArbitration`, `ResizeHandles`, `ShortcutSurfaceRow`, `FloatingControlOverlay`)
- **Persistence:** stable IDs + logical cells/spans only; never `Drawable`/`Intent`/`UserHandle`/pixels/Compose state
- Deps `app → feature → core`, `app → platform → core`; no feature-to-feature impl deps; `:app` wires only — never a dumping ground; column count N/A to gestures/feed, five-column to grid/tile math.

---

## Phase 0 — Prerequisite: Phase 4 closeout (not part of fixing doc, must come first)

**Owner:**Release/integration. **Why:** `CURRENT.md:10` + planners agree: do not interleave fixing edits with uncommitted W2/W4.

- Commit W2+W4 worktree, `git status --short` clean, update `CURRENT.md` to Phase 5 readiness
- Device manual validation for Phase 4 (drag/resize/unpin/origin) on SM-G986W — record model/serial
- Exit gate: closeout handoff linked from `CURRENT.md`, fixing phases start from clean tree

---

## Phase A — Gesture gating + single arbitration (`§1` + `§6` page-nav part)

Goal: page nav lowest priority, only from bottom 33% when not editing; tile touch never pages; one winner per gesture.

### A1 — Disable page swipe while editing/holding
**State owner:** `feature:home` transient → `Boolean homeHold` to shell. **UI:** `feature:home`. **Platform/Grid/Persistence:** untouched.
- Files: `feature/home/HomeCanvas.kt` add `onTransientHoldChange: (Boolean)->Unit`; `feature/home/HomeCanvasGrid.kt` derive `isHold = active!=null || interaction is Editing || surface is Shown` via `LaunchedEffect`; `app/LauncherShell.kt` `var homeHold remember{}` + `userScrollEnabled = !picker.blocksPager() && pagerWinner!=PICKER && !homeHold`
- Exit: long-press/wiggle/shown-surface → swipe never pages; pill `animateScrollToPage` still works; Back/empty-tap → re-enabled; no tap/launch/scroll/packing change
- Validation: `assembleDebug`, `test` (hold-callback + `userScrollEnabled` logic), `lint`; manual: edit → swipe stays, exit → swipes again

### A2 — Single arbitration: tile touch never becomes page swipe
**State:** feature transient lock. **UI:** feature + pure predicates in `core:designsystem`. **App:** consumes `Boolean` only.
- Files: `feature/home/HomeGestureArbitration.kt` add `holdsPager` mapping (all winners except `SCROLL`/`BACKGROUND` hold); `feature/home/HomeCanvasGrid.kt` route down-hit/tap/select-drag/active move-resize through `resolveHomeGesture`, hoist `pagerHold`, unify tap-cancel `:401` + idle `:430` + drag `:526`; `app/LauncherShell.kt` OR `pagerHold` into `userScrollEnabled`; `core/designsystem/GestureArbitration.kt` read-only unless pure mapping needs home for tests
- Exit: tile-origin press (tap/hold/move/resize/select-drag/flick) never pages even with horizontal motion; background-origin still pages (pre-A3 anywhere, post-A3 bottom-zone only); one winner, no mid-gesture flip; priority `UNPIN>HANDLE>ACTIVE_RESIZE>ACTIVE_MOVE>SELECT_DRAG>TAP>SCROLL`
- Validation: `assembleDebug`, `test` (`holdsPager` + `resolveHomeGesture` priority), `lint`; manual: tile-drag never pages, gap-drag does

### A3 — Bottom-33% start region
**State:** transient shell `remember` per-gesture. **UI:** shell gate + pure primitive. **Grid/Feature:** untouched except A1-A2 signals.
- Files: `core/designsystem/GestureArbitration.kt` add pure `isBottomRegionStart(yPx,heightPx,fraction=0.67f)`; `app/LauncherShell.kt` down-Y observer → `pagerRegionAllowed` ANDed into `userScrollEnabled`; `app/FeedSurface.kt` read-only confirm
- Exit: top-67% start never pages on any page; bottom-33% pages; edit/tile-hold still suppress even bottom starts; pill always works; no per-page special case
- Validation: `assembleDebug`, `test` (boundary/zero-height/out-of-range), `lint`; manual: top swipe fails, bottom strip swipes, editing blocks everywhere

Device proof for Phase A (NOT-RUN now, required at implementation): (1) edit → swipe fails, Back → works; (2) tile-drag never pages, gap-drag does; (3) top never pages, bottom does — pill works throughout.

---

## Phase B — Tile interaction rework (`§2`+`§3`+`§4`+`§5`)

### B1 — Remove swipe-to-open shortcuts (§2)
**Owner:** `feature:home` transient only.
- Files: `feature/home/HomeCanvasGrid.kt:416-510` delete flick-to-show (`cellForPixel`+`classifySwipeFlick`+`showSurface`), `:885-901` delete `SwipeSurfaceOverlay` call-site, `:149-151,379,888-900` retire `surfaceEntries/onSurfaceExecute/onSurfaceTogglePin` via `HomeCanvas.kt:40,61,94` + `LauncherShell`; `feature/home/SwipeSurfaceState.kt` retire `showSurface/classifySwipeFlick/swipeSurfaceFrame` + thresholds, delete file if orphaned; keep `ShortcutSurfaceRow` + `ShortcutSurfaceEntry` for B4; update `SwipeSurfaceStateTest`/`SwipeSurfacePlacementTest` (no NO-SOURCE pass)
- Exit: no direction/idle/editing swipe opens shortcuts; `grep classifySwipeFlick|showSurface|surfaceEntries` only historical/B4-reuse; tap/long-press/scroll unchanged; no persistence/catalogue change
- Validation: full `assembleDebug/test/lint + repo_map/module_graph/function_audit/compose_audit`; device: flick 1×1+2×2 shows nothing, long-press still edits

### B2 — Restore edit-mode move + selection separation (§4)
**State:** `HomeEditSession MOVE` + `Editing(selectedId)`. **Grid:** `GridRules.move` sole legality. **Persistence:** existing `onMoveTile/onRemoveTile` stable-ID + logical-cell path.
- Files: `HomeCanvasGrid.kt:526-752` body branch authoritative MOVE when `!isOnHandle`, no mid-gesture RESIZE promotion, keep steal-cancel zero-write; `HomeCanvasTile.kt:142-184` in-Editing tap → `onSelect`, never launch/resize/page; `HomeInteractionController.kt:54-59,102-108` select-on-tap + `unpinIfSelected`; `HomeEditSession.kt:119-135,195-218` last-valid hold; `HomeGestureArbitration.kt:22-68` priority; `MoveDragMath.kt` `cellForPixel` only
- Exit: body-drag moves, handle never from body, tap-other reselects, unpin removes + stays in edit, Back/empty exits; body never navs/shortcuts/resizes; holes preserved, unrelated tiles identical; invalid holds last-valid zero-write
- Validation: same 7 commands, `:feature:home:testDebugUnitTest` first (`HomeEditSessionTest`, `MoveDragMathTest`, `HomeGestureArbitrationTest`); device: free-cell commit survives restart, colliding snap-back, tap-B switches, unpin removes

### B3 — Harden dedicated bottom-right resize handle (§3)
**State:** feature session. **Grid:** `GridRules.resize`. **Designsystem:** visual only.
- Files: `core/designsystem/ResizeHandles.kt:17-26` visual only (16dp visual/48dp touch per `RESIZE_TOUCH_DP :915`, no detector — preserves slop-race doc `:838-844`); `RemoveButton.kt` unchanged, pos via `removeButtonOffset :1407-1449` (1×1 top-left `(×)` kept, larger centered unless constraints force move); `HomeCanvasGrid.kt:545-604` origin snapshot + `isInHandle :1332-1366` inward-only + once-at-start routing, `RESIZE :604-685` via `ResizeDragMath.rawSpan`+`quantize`+`updateResizeCandidate` last-valid, commit `resizeCommitSpan→onResizeTile` stay-in-edit; `HomeEditSession.kt:92-106,148-163,227-250`; `ResizeDragMath.kt:34-73` origin-fixed
- Exit: only handle resizes selected tile; body never resizes; unpin per-span rule kept; collisions/minima/bounds via `core:grid`; invalid keeps preview + error overlay zero-write; no swipe-as-resize; logical span persisted only
- Validation: same 7 + `ResizeDragMathTest`, `ResizePreviewSpanTest`, `IsInHandleTest`; device: 1×1→2×1/2×2 commits, colliding blocked, edge handle reachable, unpin reachable

### B4 — Edit-mode shortcut menu above selection (§5)
**State:** transient menu following `selectedId`. **Platform:** read-only discovery (`platform:shortcuts` gateway only if `surfaceEntries` empty-gap requires it, `LauncherApps.getShortcuts`-backed). **Model:** `ShortcutRef` IDs only. **Designsystem:** reuse rows.
- Files: `HomeCanvasGrid.kt:885-901` replacement `EditShortcutMenu` sibling overlay above tile via `swipeSurfaceFrame` try-beside/flip/clamp or minimal above-anchor variant (overlay only, no reflow); `ShortcutSurfaceRow.kt:38-48,85-187` reuse as-is (sibling launch/pin, 48dp, basic OK); `HomeCanvasTile`+`InteractionController` follow `selectedId` (change dismisses old+shows new; exit/move/resize dismisses zero-activation); `ShortcutOrdering.kt:32-45` visible/overflow if pins shown; discovery: if honest `No shortcuts yet :1210-1216` insufficient, thin domain interface wired via `:app` — no feed/search/grouping/gating here; none → no menu (no empty box)
- Exit: with-shortcuts shows above, without shows nothing; selection transfers, exit/move/resize dismisses zero activations; menu never moves/resizes/pages; direction `app→feature→core` held
- Validation: same 7 + `ShortcutOrderingTest`, `ShortcutSurfaceRowTest`, new follow/dismiss Compose tests; device: above-menu, none→nothing, drag dismisses no-launch, exit dismissed

---

## Phase C — Feed placeholder → provider host (§7)

Isolated from grid gestures; full-bleed provider surface, never grid cells; no second packing; `app` wiring only.

### C1 — Provider contract + availability (no binding)
**Owner:** new `platform:feed` (or `platform/launcherapps/feedoverlay/` if no new module) + `core:model`-free IDs.
- Files: NEW `FeedProviderId.kt` (package+component strings), `FeedAvailability.kt` (`Available`/`Unavailable(NoProvider|PermissionDenied|QueryFailed)`), `FeedProviderResolver.kt` (`resolve()` querying overlay service intents for Lawnfeed/Discover + Neo Feed, priority Lawnfeed>Neo Feed documented), `FeedProviderResolverTest.kt` (both/neither/failure fakes); EDIT `settings.gradle.kts` if new module
- Exit: `Available` iff known service resolves (deterministic priority); else truthful `Unavailable`; 4-case unit cover; `module_graph` shows no edge to `feature/*` or `core:grid`
- Validation: `:platform:feed:test`, `lint`, `module_graph`; device NOT-RUN: install/uninstall Lawnfeed flips `Available/Unavailable` (instrumented pass needed before C2 closes)

### C2 — Overlay host gateway (lifecycle + callbacks, behind seam)
**Owner:** `platform:feed` gateway + thin `:app` lifecycle wiring.
- Files: NEW `FeedOverlayGateway.kt` (`state:StateFlow<FeedAvailability>`, `start/stop`, attach/detach), `FeedOverlayGatewayImpl.kt` (bind/unbind resolved service, forward show/hide/progress, package-change invalidation; all `Context/ServiceConnection` types here never leak to composables/`core:model`), `FeedOverlaySurface.kt` (`AndroidView` host taking `FeedProviderId`+modifier, provider draws content, no grid/arbitration); EDIT `app/AndroidManifest.xml` (overlay permission+`<queries>` co-introduced), `app/build.gradle.kts` (`implementation(platform:feed)`)
- Exit: install → `Available`+attach, uninstall/disable → `Unavailable`+detach zero-crash no pager/grid involvement; single connection stream; home/picker tests unchanged
- Validation: `assembleDebug/test/lint/repo_map/module_graph` (must show `app→platform:feed`, no `feature:home→platform:feed`, no `platform:feed→core:grid`); device NOT-RUN: Lawnfeed then Neo Feed attach/detach via `launcher_smoke`/manual page-0

### C3 — Shell swap: placeholder → host + honest fallback (wiring only)
**Owner:** `:app` composition only.
- Files: EDIT `app/FeedSurface.kt:22-33` (keep `FEED_SURFACE_TAG:12`+signature; `when(availability){Available->FeedOverlaySurface; Unavailable->BasicText("Feed unavailable")}` transparent fallback); `app/LauncherShell.kt:163-165` pass gateway state to page 0 (IDs/count/`beyondViewportPageCount`/pill unchanged); `app/MainActivity.kt:166-177,219-231` construct gateway, `start onStart/stop onStop`, collect state (mirrors loader pattern); NEW `app/androidTest/FeedSurfaceTest.kt` (tag exists, fallback text, Available slot hosts without pill/count change)
- Exit: with provider page 0 shows real surface not placeholder; without shows `Feed unavailable` as today; pill navigates 3 pages; no gating behavior change; `git diff --stat` only 3 app files +1 test; `function/compose_audit` clean
- Validation: `assembleDebug/test/lint/function_audit/compose_audit`; device NOT-RUN: side-by-side capture installed vs uninstalled required for closeout; unit alone does not prove rendering

Sequence C1→C2→C3 strictly after Phase-4 closeout + device validation; do not overlap with A/B.

---

## Suggested execution order + why

1. **0 → A1+A2 → B1+B2** — unblocks edit reliability first (disable pager in edit, kill shortcut-swipe race, restore move). Smallest slices that make edit testable.
2. **A3 + B3** — region gate + dedicated handle (both depend on stable arbitration, independent of each other once A2 lands).
3. **B4** — shortcut menu last in tile track (needs selection/move/resize stable + swipe removed so dismiss semantics are clean).
4. **C1→C2→C3** — feed host anytime after 0 in parallel track (isolated files, no gesture overlap) but close out after A to prove no competing gesture path.

Total: 3 gating + 4 tile + 3 feed = 10 independently committable slices + Phase-0 closeout. Each slice: one owner, one exit gate, full `assemble/test/lint + repo_map/module_graph/function_audit/compose_audit` + focused module tests + stated manual/device proof. No `app/` logic dumping, no ViewModel/manager god-object, no second grid/packing system, five columns throughout.

Next action with exit condition: commit W2+W4 + run Phase-4 device validation and link handoff from `CURRENT.md`; then assign A1 (`feature/home` hold signal + `app` wire) as first fixing slice — done when edit-hold blocks pager swipe while pill still navigates.
