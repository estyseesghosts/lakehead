# 2026-09-24 — D3 seeded Home device evidence
Status: in progress
Owner/slice: D3 test-owned seeded production-Home device evidence — [`d3-viewport-aware-home-rendering.md`](../../docs/agents/tasks/d3-viewport-aware-home-rendering.md)

## Baseline and worktree

Test-only path: `app/src/androidTest/java/me/foxtails/lakehead/MainActivitySeededHomeDeviceTest.kt`. Commit `e810f6d5ffd05fc53a4560fb4f9fd02e9f4a1eee` has parent `afa27d8c4549b6274a218834947e4ff2fac1d691`; the prior D3 app-migration commit is `dfa4fec85617dda4d2c585adca4afd9dab97516c`. The three intentionally untracked local artifacts remain `.opencode/agents/._TEMPLATE.md`, `.serena/.gitignore`, and `.serena/memories/`. No D4 work was started.

## Completed and decisions

The device test explicitly seeds `PRODUCTION_HOME_ID` (`HomeId("home-main")`) into `placements.db` before deferred Activity launch. Seeding is test-owned only: there is no production Home provisioning or catalogue synchronization. The test covers:

- NotFound with no placement rows, including no app references.
- An explicitly empty Home and floating overlay presence.
- Seeded real placements, order preservation, exact available-app dispatch, unavailable-app no-dispatch, and exclusion of unseeded catalogue entries.

Review hardening includes bounded waits, a complete no-write check, safe capture, and adaptive ordering. Loading is covered by unit/feature tests but is not asserted transiently on device. The production contracts and phase boundary remain unchanged.

## Validation

Three seeded-device tests passed on SM-G986W / API 33. Device evidence is scoped to the states and interactions listed above; it does not establish the remaining D3 gate. This handoff records supplied test/commit evidence; no device commands were run for this state-record update.

## Remaining and next action

D3 remains in progress. Sparse/distant scrolling, overlay-above-canvas behavior, real profile-aware launch, visual six-column evidence, and manual accessibility checks remain unverified. D4 remains closed.

Next action: obtain the remaining D3 production-device evidence and record the results. Only after D3 is green and committed may D4 begin; do not alter the current phase boundary.
