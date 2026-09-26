# 2026-09-26 — Phase C feed host complete (code+unit, device NOT-RUN) + regression investigation (no fixes)

Status: C1+C2+C3 code+unit complete, device verification required by user before regression fixes.
Commits: 2dd13dd C1 contract, 33546d5 C2 gateway, 8a4108d C3 shell swap. HEAD 8a4108d.

C1: platform:feed module, WINDOW_OVERLAY action, lawnfeed+legacy+neofeed, priority Lawnfeed>Neo, resolver never-throws, 6 tests.
C2: gateway interface+impl (StateFlow/start/stop/attach/detach, ServiceConnection private, package receiver, single connection), FeedOverlaySurface AndroidView, manifest queries (no perm), app dep, 8 gateway tests (14 total feed).
C3: FeedSurface availability param, LauncherShell pass-through page0, MainActivity lazy+collect+start/stop, FeedSurfaceTest 3 tests androidTest NOT-RUN, 2 caller fixes.

Validation: assembleDebug SUCCESS, test SUCCESS, lint SUCCESS, :platform:feed:test SUCCESS, repo_map PASS (feed 5+2 files), module_graph app->feed only no feature->feed no feed->grid, function 4 pre-existing, compose 1 pre-existing. Device NOT-RUN: install Lawnfeed/NeoFeed flip Available/attach, uninstall detach, page0 side-by-side.

Regression investigation (NO FIXES per instruction, await device verification):
- Cannot move: onEnterEdit dead (never invoked), Active never entered, MOVE branch unreachable, onMoveTile never fires.
- Remove gone (not deleted): RemoveButton exists but only when active!=null (821-823), Active unreachable so never composes. Same for resize overlay.
- Spec gap: current remove 32dp visual top-left/centered, resize 16dp indicator bottom-right. Desired: two same-size small circles middle-pinned to corner, top-right unpin, bottom-right resize enabling move+resize.
- Fix areas after verification: HomeCanvasTile/Grid entry wiring, removeButtonOffset top-right + resize bottom-right, RemoveButton/ResizeHandles same small size (keep 48dp touch).

Next: user device-verifies Phase C, then fix move/unpin two-button spec.
