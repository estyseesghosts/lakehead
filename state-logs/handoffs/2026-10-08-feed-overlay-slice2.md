# 2026-10-08 — Feed overlay repair slice 2 (bind intent carries the host URI)
Status: completed
Owner/slice: `:platform:feed` / `docs/agents/tasks/feed-overlay-repair.md` slice 2

## Baseline and worktree
- HEAD `8c39453` (slice 1 committed). Unrelated uncommitted `:feature:home` edits, `docs/fixfeed.md` and two untracked `:feature:home` handoffs belong to other sessions and were not touched.

## Completed and decisions
- `FeedOverlayGatewayImpl.kt` `AndroidFeedConnector.bind`: the explicit-component intent now also carries `.setData(Uri.parse(FeedHostUri.build(context.packageName, Process.myUid())))`. Reuses the slice-1 builder; no new URI logic.
- `FeedOverlayDiscoveryDeviceTest.kt`: two per-provider tests, `neoFeedAloneAcceptsAndConnects` and `lawnfeedAloneAcceptsAndConnects`. Provider scoping is done by wrapping the real `AndroidFeedServiceQuery` in a `FeedServiceQuery` that answers `absent()` for every package outside the provider's set, so no uninstalling or disabling was needed and the device state is untouched. Each test runs the real gateway on `Dispatchers.IO`, waits (10 s, `withTimeoutOrNull`) for `bindingEvidence.connected` or a non-null `diagnostic`, then asserts: `diagnostic == null`, `evidence.target.provider` is the expected one, `accepted`, `connected`. A rejection fails the test with provider, evidence and diagnostic in the message; nothing is skipped.
- Connected-proof, not `bindService` acceptance, is the gate (slice 1 showed acceptance alone reaches gateway `Available`).

## Validation
- `./gradlew :platform:feed:connectedDebugAndroidTest` on R5CN70E2SGV (SM-G986W, Android 13): 5 tests, 0 failures, none skipped (result XML checked). Includes both new tests, so accepted + connected held for Neo Feed alone and Lawnfeed alone (Lawnfeed resolves to `amirz.aidlbridge.BridgeService`).
- `./gradlew assembleDebug`, `./gradlew test -q`, `./gradlew lint -q`: all exit 0 (lint wrote reports only).
- `repo_map.py`, `module_graph.py` (no new edges), `function_audit.py` (only pre-existing flags, including two already in `FeedOverlayGatewayImpl.kt` at :608 and :1109), `compose_audit.py` (pre-existing doc-comment hits only): run.
- Not run: release-variant device run, install/removal re-resolution, real feed rendering.

## Remaining and next action
- Next: slice 3, see `2026-10-08-feed-overlay-slice3.md`.
