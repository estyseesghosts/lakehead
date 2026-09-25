# 2026-09-24 — D3 viewport-aware Home rendering start
Status: in progress
Owner/slice: D3 implementation, coordinated across `:feature:home`, `:core:grid`, `:core:designsystem`, thin `:app` wiring, `:data:repository` read contract, and `:platform:launcherapps` catalogue/icon resolution — [`d3-viewport-aware-home-rendering.md`](../../docs/agents/tasks/d3-viewport-aware-home-rendering.md)

## Baseline and worktree

D1 and D2 are complete per the existing state record and D2 closeout; D3 is specified but has not been implemented. At activation, `git status --short` reported the repository as all-untracked (top-level entries include `.github/`, `.opencode/`, `.serena/`, `app/`, `core/`, `data/`, `docs/`, `feature/`, `platform/`, `state-logs/`, `tools/`, and build/config files). No files were staged and no commit was made. No source or configuration change is part of this activation.

## Intended bounded objective and decisions

Implement the D3 read-only rendering slice: render placements loaded for one explicitly supplied existing `HomeId` in a viewport-adapted, vertically scrolling sparse Home canvas, retaining honest loading, loaded-empty, and not-found states, stable persisted coordinates, profile-aware identity, metadata, and launch behavior. D3 does not create/select/provision Homes or modify persisted placements; D3 alone does not complete the Phase D gate.

The agreed Home-route decision is to replace the Phase A Home catalogue list with the persisted-placement canvas. Remove/migrate the Home-only `HomeCatalogueList` / `HomeCatalogueState` presentation and update its focused tests. Preserve the platform-owned `AppCatalogue`, `LauncherAppEntry`, profile-aware enumeration, labels, callbacks, and icon-resolution responsibility for metadata and future All Apps use. Catalogue entries must not appear as Home tiles unless corresponding persisted placements exist. Do not build All Apps or a duplicate/fallback catalogue list in Home.

Ownership and dependency boundaries follow the D3 specification: `:feature:home` owns loading orchestration, immutable presentation/render state, Home arrangement, launch dispatch, and transient scroll state; `:core:grid` owns framework-free viewport adaptation, logical extent and safe row-window arithmetic; `:core:designsystem` owns sparse/rebased Compose layout and reusable tile/floating primitives; `:app` supplies the selected `HomeId` and thin lifecycle/dependency wiring; `:data:repository` remains the owner of explicit persisted reads; `:platform:launcherapps` remains the owner of profile-aware catalogue and icon resolution/cache. Dependencies remain inward; no persistence/platform/Compose leakage into core model, no duplicate grid policy, and no repository work or platform lookup in composition.

## Validation

NOT RUN for D3 implementation: focused tests, `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`, repository/module/function/Compose audits, device installation, and runtime/device behavior. This activation only inspected state/specification and worktree status; prior D2 results are recorded in the D2 closeout and were not rerun here.

## Remaining and next action

D3 implementation has not started. Exact next operation: inventory the current Home route and its focused tests, plus the D2 repository read contract and platform catalogue/icon callers, before making implementation edits; identify the concrete production Home replacement points and verify no pre-existing work is overwritten. Exit condition: the first implementation change is owned by its module and keeps the platform catalogue available while replacing only Home's catalogue-list presentation. Device behavior, including a provisioned test Home, remains unverified.
