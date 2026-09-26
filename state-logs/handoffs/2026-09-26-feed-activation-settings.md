# 2026-09-26 — Feed activation + settings selector (code+unit, device NOT-RUN)
Status: Phase C follow-up complete; device verification pending.
Owner/slice: user device verification of feed activation and settings selector, followed by bounded home regression fixes.

## Baseline and worktree
Commits: `7189aeb` F0 auto-attach, `4d6abda` S1 settings menu. HEAD: `4d6abda`.
No code edits were made for this handoff. Existing unrelated worktree changes remain untouched.

## Completed and decisions
- F0 feed activation: gateway start/package-change auto-binds `Available`, switching rebinds, and rejection transitions to `QUERY_FAILED`; six tests were added, bringing feed coverage to 20 unit tests.
- S1 settings selector: button uses nullable-long-press `combinedClickable`; the pill supports middle long-press; `SettingsMenuOverlay` is a themed scrim sibling with tags, 48dp rows, selected `Available` state, and an `Unavailable` footer.
- Shell wiring gates the settings menu with `isSettingsOpen`; `MainActivity` attaches the wiring. PagerGate gained four tests and Selection gained five tests.
- Two Android tests cover the settings flow, but device execution remains NOT-RUN (10 device tests total).

## Validation
- `./gradlew assembleDebug` — SUCCESS.
- `./gradlew test` — SUCCESS.
- `./gradlew lint` — SUCCESS.
- Feed/app unit tests — SUCCESS.
- `python3 tools/scripts/repo_map.py` — PASS; app depends on feed only for this boundary.
- `python3 tools/scripts/module_graph.py` — PASS.
- Function and Compose audits — clean apart from four pre-existing function findings and one pre-existing Compose finding.
- Device validation — NOT RUN: install provider and verify auto-bind, long-hold Home opens settings, selection switches host, Back/scrim dismisses, and uninstall fallback.

## Remaining and next action
- Active user verification is still required before regression changes.
- Regression investigation remains intentionally unfixed: move is dead because `onEnterEdit` is never invoked and `Active` is unreachable; remove is reachable only when `Active` and is therefore hidden. The spec also differs on 32dp versus 16dp controls and top-left/center versus top-right/bottom-right placement.
- Next action: user runs the feed and settings device proofs; exit when auto-bind, host switching, dismissal, uninstall fallback, and feed behavior are recorded PASS/FAIL, then implement the bounded move/unpin two-button fixes.
