# 2026-09-24 — C1 geometry and layout primitives complete
Status: completed
Owner/slice: `:core:designsystem` / Phase C C1 geometry and layout primitives

## Baseline and worktree

Phase A / 0B and Phase B (B1–B3) remain complete. The C1.1, C1.2 and C1.5 baseline is inherited from the prior [C1 UI geometry continuation](2026-09-24-c1-ui-geometry-continuation.md): `SixCellGeometry`, `TileBounds` and `SafeControlFloor`, with their existing unit tests. This handoff closes C1 after the C1.3/C1.4 additions and device instrumentation. The repository worktree is all-untracked; no files were staged and no commit was made. C2 and later phases remain open.

## Completed and decisions

- C1.3 `SpanContainer` is implemented in `core/designsystem/src/main/java/me/foxtails/lakehead/core/designsystem/SpanContainer.kt`. `core/designsystem/src/androidTest/java/me/foxtails/lakehead/core/designsystem/SpanContainerTest.kt` contains 10 Android instrumentation tests covering span measurement, placement, extents, empty content, invalid origins, overlap and overflow. Placement legality remains owned by `:core:grid`.
- The overflow test invokes `TileBounds.bounds` directly to check the synchronous boundary. Overflow raised during Compose measurement would be delivered asynchronously on the UI thread and cannot be reliably caught with `assertThrows` around composition setup.
- Negative-origin checks are independent tests for X and Y (`negativeOriginXIsRejected` and `negativeOriginYIsRejected`) so each axis has separate instrumentation coverage.
- C1.4 `FloatingOverlayFrame` is implemented in `core/designsystem/src/main/java/me/foxtails/lakehead/core/designsystem/FloatingOverlayFrame.kt`; its unit tests in `core/designsystem/src/test/java/me/foxtails/lakehead/core/designsystem/FloatingOverlayFrameTest.kt` number 8.
- Android test runner/dependencies are configured in `core/designsystem/build.gradle.kts`.
- The geometry/layout slice is complete within `:core:designsystem`. No C2 or subsequent phase is claimed complete. Accessibility and visual checks beyond geometry/device instrumentation remain separate follow-up work.

## Validation

- `./gradlew :core:designsystem:assembleDebugAndroidTest` — PASS on SM-G986W, API 33.
- `./gradlew :core:designsystem:connectedDebugAndroidTest` — PASS on SM-G986W, API 33: 10 passed, 0 skipped, 0 failed. The test harness emitted a nonfatal `appops` warning; the instrumentation suite completed successfully.
- `./gradlew :core:designsystem:lint` — PASS.
- Final cumulative root validation and audits are pending the final validation agent and are **NOT RUN in this handoff**: `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`, `python3 tools/scripts/repo_map.py`, `python3 tools/scripts/module_graph.py`, `python3 tools/scripts/function_audit.py`, and `python3 tools/scripts/compose_audit.py`.

## Remaining and next action

C1 geometry/layout primitives are complete. C2 and later roadmap phases remain open. Accessibility and broader visual verification are not established by these geometry-focused unit/instrumentation results. The one next action is for the final validation agent to run the cumulative root build, test, lint and audits listed above and record their exact results; exit condition: every command has a truthful PASS/FAIL/NOT RUN result, with any failures or follow-up gaps explicitly carried forward before beginning C2.
