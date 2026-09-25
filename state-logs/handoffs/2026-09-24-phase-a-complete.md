# 2026-09-24 — Phase A / 0B launcher-platform gate completed
Status: completed
Owner/slice: Phase A / `:app`, `:feature:home`, `:platform:launcherapps`; handoff to Phase B B1 / `:core:grid`.

## Baseline and worktree

This is a documentation/state update after the verified Phase A gate. The worktree is all-untracked with no commits; no staging or commit was performed. This session did not inspect Git, per the assigned scope. Phase A evidence below was supplied from the completed device and validation pass; product tests were not rerun for this documentation-only update.

## Completed and decisions

- A2 catalogue presentation is complete. Five connected feature tests and the app tap-identity instrumentation test passed on API 33. Labels, loading/empty states, and click semantics were verified.
- A3 live package mutation is complete. The isolated `:fixtures:launchable` fixture and narrow production `MAIN`/`LAUNCHER` queries were used for real install/uninstall observations on API 33. The entry appeared and disappeared while the same Activity instance remained alive. The exact A3 preparation commands remain in the task specification:
  - `env ANDROID_HOME=/Users/terezi/Library/Android/sdk ./gradlew :fixtures:launchable:assembleDebug`
  - `adb push fixtures/launchable/build/outputs/apk/debug/launchable-debug.apk /data/local/tmp/protolauch-a3-fixture.apk`
- A4 correct-profile proof is complete on PLG110 (API 36): personal profile 0 and managed work profile 11 exposed the shared `com.android.contacts/.DialtactsActivityAlias` as distinct identities; work launch was observed under `u11`; and an inaccessible token returned false without a new personal ActivityRecord. Direct proof is split between `WorkProfileLaunchProofTest.kt` and `WorkProfileDeviceEvidence.kt`; function audit was clean. On Samsung API 33 with no work profile, the A4 test explicitly skips.
- Full Phase A gate is complete. On SM-G986W, process recreation changed PID 5710 → 5797; `MainActivity` resumed and catalogue labels were visible after restart. Samsung TalkBack smoke passed with Samsung TalkBack enabled: the UI hierarchy exposed clickable/focusable actions and rows, and keyboard focus traversal worked. Speech output was not verified.
- HOME-role evidence had shown ProtoLaunch as the sole holder on PLG110 before later test cleanup. This is historical evidence only; current PLG default state after cleanup is not claimed.
- Samsung connected app suite: 5 total, 4 passed and 1 A4 skip (no work profile on that device). Mandatory static validation passed:
  - `./gradlew assembleDebug` — PASS.
  - `./gradlew test` — PASS, 29 unit tests.
  - `./gradlew lint` — PASS, 0 errors and 31 warnings.
  - `python3 tools/scripts/repo_map.py` — PASS.
  - `python3 tools/scripts/module_graph.py` — PASS.
  - `python3 tools/scripts/function_audit.py` — PASS.
  - `python3 tools/scripts/compose_audit.py` — PASS.
- The `appops set ... androidx.test.services` warning was harness-only; product has no external-storage permission.
- A5 settings persistence remains deferred by user approval. No speculative settings are to be introduced. Phase A / 0B is complete and Phase B may begin, but no later phase is marked complete.

Touched paths in this durable update:
- `docs/roadmap.md`
- `docs/agents/tasks/0b-launcher-platform.md`
- `state-logs/CURRENT.md`
- `state-logs/handoffs/2026-09-24-phase-a-complete.md`

## Validation

- Product validation above is the reported gate evidence; it was not rerun in this documentation-only update.
- Focused consistency review of the changed roadmap/task/current/handoff wording — PASS. Stale Phase A-open/A4-open statements are absent from the roadmap and task spec; CURRENT points to the new handoff and B1, and roadmap/task/CURRENT links resolve to the intended files.
- Device speech output — not verified.
- Current PLG110 HOME default-holder state after test cleanup — not verified and not claimed.

## Remaining and next action

No Phase A work remains. A5 settings persistence remains an approved deferral. One concrete next action: begin B1 in `:core:grid`, implementing the coordinate, supported-span, bounds, collision, and move/resize legality model with focused tests for edges, invalid spans, overlap, holes, same-item movement, replacement versus rejection, and unchanged unrelated entries. Exit condition: B1 legality behavior and tests are independently reviewed and passing; do not claim B2/B3 or later phase completion.
