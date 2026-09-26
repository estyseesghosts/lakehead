# 2026-09-26 — Feed honesty + theme text (code+unit, device NOT-RUN)
Status: completed; device verification pending.
Owner/slice: user device verification of feed selector/activation and theme readability, followed by bounded move/unpin regression fixes.

## Baseline and worktree
Commits: `735111c` honesty, `3ed2b7e` theme. HEAD: `3ed2b7e`.
This handoff records the completed code/unit slice; no additional code edits were made for the handoff. Existing unrelated worktree changes remain untouched.

## Completed and decisions
- Feed attachment re-resolves before bind.
- A false provider result falls back to `NO_PROVIDER`; exceptions map to `QUERY_FAILED` or `PERMISSION_DENIED` as appropriate.
- Added feed regressions for empty results with no bind, rejected queries, and throwing queries.
- Settings header/footer rows use `onSurfaceVariant` and `onSurface`; feed fallback uses `onSurface`.
- Alphabet letters use `onSurface` when enabled and `onSurfaceVariant` otherwise; scrim uses `scheme.scrim`.
- `NavPill` inherits the existing theme; no new components were added and no hardcoded `Black`/`White` colors remain in the changed theme surfaces.

## Validation
- `./gradlew assembleDebug` — SUCCESS.
- `./gradlew test` — SUCCESS.
- `./gradlew lint` — SUCCESS.
- Feed tests — SUCCESS.
- `module_graph` — app depends on feed only at the intended boundary.
- Audits — clean apart from pre-existing findings.
- Device validation — NOT RUN: without a provider, selector should show `No feed installed` rather than query failure and selection should do nothing; with a provider, it should show `Available`, selected, and hosted. Dark/light readability still requires a device screenshot.

## Remaining and next action
- Active user verification is required for feed selector behavior and dark/light text readability.
- Next action: user runs the device proofs; exit when no-provider and provider states, selection behavior, and screenshots are recorded PASS/FAIL, then implement the bounded move/unpin two-button regression fix.
