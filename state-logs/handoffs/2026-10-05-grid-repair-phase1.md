# 2026-10-05 — Absolute Home row semantics
Status: completed
Owner/slice: orchestrator; `:core:grid` rules/extent, with domain repository and Home callers; `docs/lakehead-grid-repair-plan.md` Phase 1 (1.1–1.4).

## Baseline and worktree
- HEAD: `3b1c59a86f2b2614b28e5371d36f261738dcd463`; existing extensive dirty/untracked tree inspected with `git status --short`. Preserve all pre-existing changes; no staging/commit requested.
- User explicitly selected the newer repair plan, superseding the older movement/resize planning gate for this objective only.
- `.opencode/agents/orchestrator.md` and `two.md` are deleted in the starting worktree; available `zero` maps callers, available `four` substitutes for unavailable `two` for narrow implementation slices.
- Serena `initial_instructions` is not exposed in the tool catalog; discovery attempted, no substitute manual invented.

## Completed and decisions
- Read repair plan, repository instructions, current state and linked historical handoff, architecture/design and state-log protocol.
- Bounded objective: establish nonnegative absolute logical rows, vertical legality independent of workspace height, extent measured from row zero, and no signed-row construction/min-row-derived origin in Home.
- Input: committed logical placements and columns. Output: bounds/collision decisions and absolute content extent consumed by repository/Home. Grid owns legality; repository owns normalization; Home owns presentation, without a second placement engine. Dependency direction remains feature/data to core.
- Acceptance: repair-plan slices 1.1–1.4; add pure regression tests before changing behavior. No geometry/editor/gesture/persistence-ack rewrite from later phases.
- `zero` mapped finite-row core/repository/app/edit callers and identified Home's signed construction and min-row origin helpers. Slice 1.1 test-only work delegated to `four`.
- Added `docs/agents/tasks/grid-repair-phase1.md` with acceptance/ownership/exclusions; added scoped contract precedence notes in `docs/architecture.md`, `docs/design.md`, and the historical `03-grid-movement-resize.md` task. Later repair migrations are explicitly not claimed implemented.
- Slice 1.1 completed/reviewed: tests in `core/grid/.../GridVerticalLayoutTest.kt`, `GridRowGapNormalizerTest.kt`, and `core/model/.../AppPlacementTest.kt` specify absolute counts and negative-position rejection. Production untouched in that slice.
- Slice 1.2 delegated: remove finite vertical legality through core/repository/app/edit callers, including MOVE vertical workspace clamping needed for workspace-independent candidates. Horizontal clamping/geometry rewrite remains Phase 2.
- Slice 1.3 independently delegated with disjoint ownership: vertical extent/projection tests only; absolute counts for nonnegative placements, retaining shared signed primitive semantics.
- Slice 1.3 implementation reviewed: `GridVerticalLayout.kt` uses absolute maximum row-exclusive for `GridPlacement`; signed-only callers retain distance semantics and mixed families reject explicitly. `GridVerticalExtent.kt` documents the distinction. `GridVerticalLayoutTest.kt` and `HomeRenderProjectionTest.kt` cover absolute row-5 counts and viewport independence. No second extent policy introduced in Home.
- Initial 1.2 integration and 1.4 implementation built/tested/linted green, but separate focused review found removed unrelated core/repository regression coverage and leftover app 4096-row configuration. Phase 1 is NOT accepted yet. Narrow follow-ups are restoring original independent tests and removing stale app/Home finite-row API plumbing.
- Slice 1.4 added core `GridContentBounds.absoluteContentBoundsOf`, Home absolute conversion and fixed row-zero renderer origin, and absolute edit workspace extent. Shared signed primitives retained. Direct core bounds regression coverage added in `AbsoluteGridContentBoundsTest.kt` for leading rows, sparse extent, empty input, horizontal edge/overflow, invalid columns and exclusive bottom beyond Int range.
- Test-recovery review rejected an initial consolidation; all 34 original GridRules and 17 original repository test methods are now retained as separate cases with columns-only migration and only obsolete finite-bottom expectations changed. Independent coverage is preserved. A final scoped test polish/high-row repository case is in progress.
- Final test polish complete: 36 GridRules cases (34 retained + two raw-bound cases) and 18 repository cases (17 retained + high-row 5000→6000 placement/move/resize/reload case). Focused core/repository suite PASS.

## Validation
- `git status --short`: completed; dirty baseline recorded above.
- `git rev-parse HEAD`: completed, hash above.
- `./gradlew :core:grid:test :feature:home:test :app:test --no-daemon`: PASS baseline before implementation (actual unit tests executed; build successful). Kotlin warnings in unrelated platform modules remain.
- Full build/test/lint, audits and device validation: not run yet. No device behavior claims.
- Slice 1.1 agent validation: baseline grid tests PASS; post-change grid tests/full `./gradlew test` intentionally FAIL with seven absolute extent assertions (old production behavior). Model tests PASS. `./gradlew assembleDebug` and `./gradlew lint` PASS; `python3 tools/scripts/repo_map.py`, `module_graph.py`, `function_audit.py`, `compose_audit.py` completed. Final green suite is still required after production migration.
- `adb devices -l`: connected SM-G986W (`R5CN70E2SGV`); no installation or behavior verification performed.
- Slice 1.3 agent validation: `./gradlew assembleDebug` PASS; `./gradlew lint` PASS; four audits completed. Targeted/full tests were blocked by compilation errors during concurrent Slice 1.2 API migration. This is not a passing test result; re-run after integration.
- Initial 1.2 test follow-up: `./gradlew :data:repository:test :feature:home:test :app:test`, full test, build and lint PASS; four audits completed. Review later identified missing regression coverage despite this green result.
- Slice 1.4 agent: targeted core/Home tests, full build/test/lint and audits PASS; `git diff --check` PASS; no Home production signed construction/min-row renderer-origin references found. Device not tested.
- Orchestrator audits after added direct bounds tests: repo map/module graph/function audit/Compose audit completed; existing complexity/informational findings remain (including known function-audit limitation from plan Phase 0; not repaired in this Phase 1 scope).
- `./gradlew :core:grid:testDebugUnitTest --tests '*AbsoluteGridContentBoundsTest' --no-daemon`: PASS (seven direct core helper regressions).
- Restored original grid/repository cases: `./gradlew :core:grid:test :data:repository:test` PASS. Full test temporarily blocked by concurrent app/Home API edits; final re-run still required.
- Orchestrator integrated `./gradlew assembleDebug`: PASS; `./gradlew test`: PASS after test restoration and app/Home API updates (cached module suites plus actual app/Home execution; fixture unit test task is NO-SOURCE, not test coverage). `git diff --check`: PASS.
- Final `./gradlew lint` and selected sparse-row Home instrumentation test launched; results pending, no device pass claimed.

## Remaining and next action
- Phase 1 slices 1.1–1.4 accepted after separate focused review: no blockers. Core/repository placement APIs and Home validation no longer take workspace row counts; app wiring has no rowsBound/4096 capacity. Add-to-home's existing core first-free search uses an occupancy-derived horizon, not a fixed capacity.
- `SignedGridPlacement` and row-window shared primitives retained because core APIs/tests still reference them. Home uses absolute `GridPlacement` and fixed renderer origin zero. `normalLegalityRows` remains unused legacy test support, not active legality; remove with later compatibility cleanup.
- Phase 2+ remains incomplete: unified metrics, raw invalid requests instead of horizontal/top clamp, frozen-scroll algebra removal, drag-time origin handling, editor/gesture decomposition, row-window/direct rendering, scroll workspace redesign and acknowledgement leases. No broader movement/resize correctness or user device-feel claim is made. The Phase 0 audit-parser fix is also not implemented in this Phase 1-only session.
- No known failing required check at close. Initial intentional red extent tests and concurrent compilation failures were resolved; they are retained above as checkpoint history.
- Worktree remains dirty with original concurrent/user work plus this slice. HEAD unchanged; nothing staged or committed.
- **One next action:** when authorized to continue, implement only repair Slice 2.1 in `:feature:home`: add the immutable pure `HomeGridMetrics` and transform tests; exit when one tested object provides forward rectangles and inverse raw-cell mapping, without migrating gestures/rendering yet.

## Final verification
- Orchestrator: `./gradlew assembleDebug` PASS; `./gradlew test` PASS; `./gradlew lint` PASS.
- `./gradlew :core:grid:test :data:repository:test` PASS after restoring the original tests; 36 GridRules tests and 18 repository cases retained/extended. High-row sole-placement 5000→6000 move/resize/reload tested. Model rejection and Home absolute extent/workspace-independent validation covered in the full suite.
- `./gradlew :core:grid:testDebugUnitTest --tests '*AbsoluteGridContentBoundsTest' --no-daemon` PASS (7 tests).
- `./gradlew :feature:home:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.foxtails.lakehead.feature.home.HomeCanvasTest#sparseDistantRow_isOmittedUntilScrolled_thenRenders` PASS on SM-G986W / Android 13; result XML confirms 1 test, 0 failures/errors/skips. Tool emitted a nonfatal `appops` warning about missing UID for `androidx.test.services`; test execution succeeded. This proves that selected sparse rendering/scroll scenario only.
- `adb -s R5CN70E2SGV shell getprop ro.build.version.release` returned `13`.
- Final `python3 tools/scripts/repo_map.py`, `python3 tools/scripts/module_graph.py`, `python3 tools/scripts/function_audit.py`, `python3 tools/scripts/compose_audit.py` completed successfully. Dependency graph unchanged; existing complexity/informational findings remain and are not hidden as repaired.
- Final `git diff --check` PASS; `git status --short` inspected. Production reference searches: no Home `SignedGridPlacement`, `legalityRows`, `moveLegalityRows`, `resizeLegalityRows`, or app `rowsBound`/4096 capacity; only remaining Home `occupiedMinRow` use is empty-extent detection, not row-origin derivation.
- No app install/manual end-to-end launcher gesture validation performed; feature instrumentation installed its test APK only.

## Touched paths for this objective
Paths below use their explicit source roots; unrelated dirty feed/design-system/shortcut visual changes are baseline work, not this objective.
- `core/grid/src/main/java/me/foxtails/lakehead/core/grid/`: `GridRules.kt`, `GridVerticalLayout.kt`, `GridVerticalExtent.kt`, `GridContentBounds.kt`.
- `core/grid/src/test/java/me/foxtails/lakehead/core/grid/`: `GridRulesTest.kt`, `GridVerticalLayoutTest.kt`, `GridRowGapNormalizerTest.kt`, new `AbsoluteGridContentBoundsTest.kt`.
- `core/model/src/test/java/me/foxtails/lakehead/core/model/AppPlacementTest.kt`.
- `data/repository/src/main/java/me/foxtails/lakehead/data/repository/HomePlacementRepository.kt` and matching test-root `RoomHomePlacementRepositoryTest.kt`.
- `app/src/main/java/me/foxtails/lakehead/`: `MainActivity.kt`, `HomeRouteLoader.kt`, `AllAppsRouteLoader.kt`, `AddToHomeCoordinator.kt`.
- `app/src/test/java/me/foxtails/lakehead/`: `HomeRouteLoaderTest.kt`, `AllAppsRouteLoaderTest.kt`, `AddToHomeCoordinatorTest.kt`, `ShortcutMenuCoordinatorTest.kt`, `ShortcutSurfaceLoaderTest.kt` (repository fake signatures only in the latter two).
- `feature/home/src/main/java/me/foxtails/lakehead/feature/home/`: `HomeCanvasGrid.kt`, `HomeEditFrontier.kt`, `HomeEditSession.kt`, `MoveDragMath.kt`.
- `feature/home/src/test/java/me/foxtails/lakehead/feature/home/`: `HomeCanvasSignedRendererTest.kt`, `HomeEditFrontierTest.kt`, `HomeEditSessionTest.kt`, `HomeNormalViewportTest.kt`, `HomePointerLoopRegressionTest.kt`, `HomeRenderProjectionTest.kt`, `MoveDragMathTest.kt`, `MoveResizeRegressionTest.kt`, `EditTransferDragTest.kt`, `ResizePreviewSpanTest.kt`.
- `feature/home/src/androidTest/java/me/foxtails/lakehead/feature/home/HomeCanvasTest.kt`: validation API signature migration only, preserving baseline visual/gesture edits.
- `docs/architecture.md`, `docs/design.md`, `docs/lakehead-grid-repair-plan.md`, `docs/agents/tasks/03-grid-movement-resize.md`, new `docs/agents/tasks/grid-repair-phase1.md`, `state-logs/CURRENT.md`, this handoff.
