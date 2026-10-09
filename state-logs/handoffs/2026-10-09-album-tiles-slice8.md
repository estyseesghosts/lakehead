# 2026-10-09 — Album tiles slice 8 (exit check, series close-out)

Status: COMPLETE. Task `docs/agents/tasks/album-tiles.md` status flipped to
Complete. No code changes in this slice except deleting the env-blocked E2E
scaffold (never committed); commit = this handoff + `CURRENT.md` + spec status.
HEAD at start `9c4aa9d` (slice 7), clean tree.

## §6 validation (exact commands + outcomes, slice-8 tree)

- `./gradlew assembleDebug` → BUILD SUCCESSFUL (up-to-date; tree identical to
  the slice-7 built tree).
- `./gradlew cleanTest test` → BUILD SUCCESSFUL; result XML re-counted fresh:
  121 files, **1577 tests, 0 failures, 0 errors** (1559 slice-6 baseline + 18
  slice-7 additions).
- `./gradlew lint` → BUILD SUCCESSFUL (executed tasks; covers all sourcesets
  incl. the new device tests).
- `python3 tools/scripts/repo_map.py` → OK.
- `python3 tools/scripts/module_graph.py` → no new edges (`:app →
  :platform:media` from slice 6 unchanged; decision-7 wiring stays in `:app`).
- `function_audit.py` → no flags on new code; `importLayout` grew ~20 lines
  (album restore next to shortcut restore) and trips the length signal —
  cohesive with its neighbors, left as is per audits-are-signals.
- `compose_audit.py` → no hits on new code.
- `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (also re-run after
  the E2E-file deletion below).

## Device pass — Samsung R5CN70E2SGV (API 33), production UI, adb-driven

Method: `adb shell input tap/swipe` + `uiautomator dump` for exact node
bounds (adopted after the (+) miss below) + `screencap` viewed after every
step. Permission pre-granted via shell (`pm grant --user 0 … 
READ_MEDIA_IMAGES`); deny leg self-revoked via shell (order-independent).
All screenshots held outside the repo and deleted afterwards — no photo
content, paths, URIs or bucket ids in tracked files. Device left with: one
unbound 3×2 Gallery tile (profile 0), photo permission granted, ProtoLaunch
not default HOME (user selects), no test photos inserted (real albums used).

1. Add: All Apps → alphabet picker → G → Gallery `(+)` (bounds-verified
   center) → Home shows 1×1 Gallery tile (`com.sec.android.gallery3d`,
   profile 0). First three `(+)` taps hit the disabled Remove placeholder
   (eyeballed x≈892 vs true AddBox center x=816 from the dump) — no-op, no
   state change; protocol from then on: dump-then-tap.
2. Resize: long-press tile → edit → tap handle to arm → drag
   (211,1098)→(511,1298) → **offer prompt "Show album photos? … Gallery?"**
   with No/Yes (proves 1×1→≥2×2 commit + `shouldOfferAlbum` on device).
3. Accept: tap Yes (pre-granted → no system dialog) → **picker "Choose album"
   with the real on-device list incl. counts** (Camera 130, Download 16,
   Instagram 3, Pictures 78, Quick Share 9, Screenshots 282, Telegram 55,
   Twitter 27, exports 115, jpg 68).
4. Pick Camera → picker closes, **tile fills with the album's newest photo,
   cropped** (production `AlbumTileCoordinator` + `MediaStoreGateway` +
   `AlbumPhotoLayer` end to end).
5. Tap advance: empty-tap exits edit; tile tap **launches Gallery (foreground
   photo viewer — the tap still launches per spec)**; back to ProtoLaunch →
   **tile shows a different photo** (advance + launch in one tap).
6. Change: long-press → edit menu shows exactly **"Change album" /
   "Remove album", no pin toggles** → tap Change → picker reopens → pick
   Instagram (3) → **tile shows a photo from the new album** (rebind proven).
7. Denied: `pm revoke` → tile falls back to its **icon (binding kept)** →
   long-press → rows still offered → tap Change → **system dialog "Allow
   ProtoLaunch to access photos and videos on this device?"** (permission is
   re-requested from the edit menu) → "Don't allow" → **no picker, menu and
   binding intact**. Re-granted via shell.
8. Remove: tap "Remove album" → **rows vanish in place (menu refresh, edit
   kept), tile back to icon**. DB ground truth pulled afterwards:
   `tile_album_bindings` 0 rows; the tile placement persists with span 3×2
   (resize commit survived).
9. Package add/remove without restart: existing
   `MainActivityPackageMutationTest` targeted via
   `adb shell am instrument -e class …` → **OK (1 test)**: fixture APK
   installed/uninstalled, catalogue updated, activity never destroyed.
10. Rotation timing (15-min slot): NOT run on device (would need a 15-minute
    wait); proven by fake-clock unit tests (`fifteenMinuteSlotAdvancesPhoto`
    incl. wrap, `noTimerWhileHomeIsHidden`).
11. Partial access (API 34+): NOT run — deferred to the user's PLG110
    (Android 16) pass per the task brief. Never claimed.

## Automation notes (kept vs removed)

- Kept and green: `:feature:home` device 63/63 (slice 7, same build),
  `:app` `AlbumMenuDeviceTest` 4/4 via targeted `am instrument` (slice 7),
  all unit suites above.
- Removed uncommitted `app/.../AlbumSlideshowDeviceTest.kt` (written as a
  slice-8 E2E scaffold, never committed): its first `waitForText` timed out
  (30 s, gallery label never visible to `createEmptyComposeRule` +
  `ActivityScenario`) then the instrumentation process died. Same failure
  class as the quarantined `MainActivitySeededHomeDeviceTest` /
  `WorkProfileLaunchProofTest` on this device (`UserHandle{150}` work profile
  vs catalogue `[0]`, phase-10 handoff) — composition works (this manual pass
  proves it), the harness cannot see it here. The manual pass above covers
  every step the scaffold contained, so deleting beats keeping a red test.
  Its useful residue: the seed it left behind confirmed DB writes work, and
  the episode produced the dump-then-tap protocol.

## Incidents and gotchas (new, device-specific)

- Dozing phone fails a whole suite with `No compose hierarchies found`
  (slice 7): wake + `svc power stayon true` for runs, restore after.
- `eyeballing tap coordinates fails`: the `(+)` glyph center ≠ touch-box
  center; always `uiautomator dump` → tap node centers.
- `connectedDebugAndroidTest` rejects `--tests` on this AGP: class-targeted
  runs go through `adb shell am instrument -w -e class … <pkg>.test/<runner>`.
- `pm` commands need `--user 0` (shell may not query user 150).
- Tapping a bound tile launches the real Gallery into the foreground; BACK
  lands on the *system* launcher (ProtoLaunch is not default HOME) — relaunch
  via `am start -n …/.MainActivity`.
- `show_touches` was enabled during aiming and turned back off; `stayon`
  restored to false; `/sdcard/ui*.xml` dumps removed.

## Touched paths (slice-8 commit)

- `state-logs/handoffs/2026-10-09-album-tiles-slice8.md` (this file)
- `state-logs/CURRENT.md` (series closed)
- `docs/agents/tasks/album-tiles.md` (status → Complete + verification note)
- Deleted (uncommitted, untracked): `app/.../AlbumSlideshowDeviceTest.kt`

## Next action

Album series closed; no album exit condition remains except the user's
PLG110 partial-access pass (outside this environment). Next work is the
user's call — candidates already on the board: widgets slice-11 remainder
(4×2 default-span device check), tile-notifications work-profile + release
APK verification. Exit condition for whichever is picked: its task-spec gate
plus a truthful handoff.
