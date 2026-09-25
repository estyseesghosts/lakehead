# 2026-09-24 — D3 feature UI prerequisites sliced complete
Status: in progress
Owner/slice: `:feature:home` D3 pure projection + HomeCanvas UI prerequisites — [`d3-viewport-aware-home-rendering.md`](../../docs/agents/tasks/d3-viewport-aware-home-rendering.md)

## Baseline and worktree

The D3 starting handoff records the repository baseline as all-untracked, with no files staged and no commit. No Git inspection or mutation was performed for this handoff; the starting record is preserved as historical context. No commit was made.

## Completed work and ownership

The `:feature:home` slice now has a pure `HomeRenderProjection` for grid-owned viewport adaptation, extent, row-window delegation, placement-order preservation, complete-profile-identity metadata joins, and loading/not-found/loaded state projection. `HomeCanvas` and its canvas/grid/tile/icon presentation files provide UI prerequisites for the read-only persisted-Home canvas, including honest states, unavailable tiles, launch callbacks, and a floating HOME-role request control. Focused projection and canvas tests are present.

The Phase A Home catalogue list remains the current production route because app migration is intentionally the next bounded slice. `HomeCanvas` is not yet wired into `MainActivity`; the old `HomeCatalogue` presentation files and tests remain. The platform `AppCatalogue` / `LauncherAppEntry` inventory remains intact for metadata and future All Apps use. Thus these module UI prerequisites do not complete D3's production-route migration or exit gate.

## Validation

The available feature-module reports supplied by the implementation agents record:

- `:feature:home:testDebugUnitTest`: 25 `HomeRenderProjectionTest` tests, 0 failures, 0 errors, 0 skipped.
- `:feature:home:connectedDebugAndroidTest`: 15 `HomeCanvasTest` tests passed on SM-G986W / Android 13; the existing `HomeCatalogueTest` also remains and its report shows 5 passed, 0 failures, 0 skipped.
- Feature lint report: 0 errors and 6 dependency-freshness warnings.

These are report results, not commands run by this handoff author. Root `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`, and the repository/module/function/Compose audits were not rerun after these slices. No D3 production APK was installed after these changes.

## Device evidence

The supplied report of a transient device launch loop was not reproduced. One live launch had no ProtoLaunch fatal exception. The user requested deferral of further investigation; this observation is not evidence that the production Home migration or D3 device gate passed.

## Deferred/unverified behavior

Production `MainActivity` wiring and end-to-end loader/reload/icon-decode/metadata flow remain unverified. The production route still uses the Phase A catalogue list, so a production D3 Home canvas was not exercised. No D3 production APK was installed after these changes. Device behavior for persisted populated, empty, and not-found Homes, sparse distant scrolling, and launch through production wiring remains unverified.

## Known limitations and follow-up

D3 remains in progress and is not complete. The UI prerequisites do not themselves load a selected persisted `HomeId`, supply resolved metadata/icons, or replace the production route. The catalogue-list migration and its presentation/test cleanup have not occurred. Do not begin D4.

Next action: a fresh `:app` app-migration owner must plan and implement the thin loader/reload/icon-decode/metadata wiring, swap production Home to `HomeCanvas`, and migrate/delete the `HomeCatalogue` presentation and focused tests. Then run focused tests, root assemble/test/lint/audits, and install/validate on device. Exit condition: production Home is wired to the persisted-placement canvas through thin app-owned dependencies, old Home catalogue presentation/tests are migrated or removed, and focused/root/device/install validation is recorded; keep D3 in progress until its full gate is satisfied.

## Worktree state

This handoff and `state-logs/CURRENT.md` are the only paths changed by this state-log update. The all-untracked/no-commit baseline comes from the D3 start handoff; current Git status was not checked here. No staging or commit was performed.
