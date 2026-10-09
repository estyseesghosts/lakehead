Feed overlays are not wired up correctly: discovery fails before anything binds, and behind that the overlay protocol is only partly implemented. Discovery is confirmed on the device; the protocol gaps come from reading the code and still need device proof.

## What I found

**1. Discovery is broken (confirmed on R5CN70E2SGV, Android 13).**
Providers declare their overlay service with an `app` data scheme, and the launcher queries without any data, so nothing matches:

| Query | Result |
| --- | --- |
| `WINDOW_OVERLAY`, no data (what `AndroidFeedServiceQuery.kt:47` sends) | No services found |
| `WINDOW_OVERLAY` with `-d app://me.foxtails.lakehead:10000` | 4 services: Google app, `amirz.aidlbridge`, `app.lawnchair.lawnfeed`, `com.saulhdev.neofeed` |

- Neo Feed's filter on the device is `Action: WINDOW_OVERLAY, Scheme: "app"`.
- The same data-less intent is used by the preflight (`FeedOverlayGatewayImpl.kt:1867`) and by the manifest `<queries>` entry.
- The expected result is "Feed unavailable" even with three providers installed, which matches the symptom in `docs/02.md`. I did not look at the screen or logcat to confirm what the launcher shows.

**2. The bind would fail next (code reading).**
`AndroidFeedConnector` binds with an explicit component and no data (`FeedOverlayGatewayImpl.kt:1838`). Neo Feed targets SDK 37, so Android 13 should reject an explicit intent that doesn't match its filter. The providers also read the host package and uid from that URI.

**3. Protocol gaps behind that (code reading, compared against the Launcher3 client from memory).**
- **Window attach flags:** `WINDOW_ATTACH_FLAGS = 0`. In the reference client, bit 0 of the options means "overlay enabled", so 0 likely asks for no feed. `windowAttached2` is never used.
- **Scroll verbs:** `startScroll` and `endScroll` are never called; the bridge only sends `openOverlay`/`closeOverlay` and a bare `onScroll`.
- **Lifecycle:** `onResume`, `onPause` and `setActivityState` are never sent.
- **Provider callbacks:** `scrollEvents` and `statusEvents` in `AidlFeedBridge` have no collectors. If the user swipes the feed closed inside the provider's window, the pager would stay on a blank page 0.

**4. The provider list is hard-coded.**
`FeedProviderId` knows only Lawnfeed (two package ids) and Neo Feed. `amirz.aidlbridge` is installed on the device and can never be picked, which conflicts with the "don't special-case one provider" rule in `docs/01.md`.

**5. Why it looked done.**
- The 2026-09-28 handoff records the provider matrix as never run; it was waiting on approval to install the providers.
- The 16 passing `FeedSurfaceTest` cases and the feed unit tests run against fakes.
- `gateway.diagnostic` and `bindingEvidence` have no readers in `:app`, so the nine-state diagnosis never reaches the UI or logs.

## Plan

All slices are owned by `:platform:feed` unless noted, with `:app` doing wiring only. Each one runs the AGENTS.md §6 validation and ends with a handoff. Before slices 4–6, the implementer should check the exact flags and call order against Lawnchair's `LauncherClient` at the commit already cited in `AidlFeedBridge.kt`, since my protocol details are from memory.

| # | Deliverable | Exit gate |
| --- | --- | --- |
| 0 | Task spec `docs/agents/tasks/feed-overlay-repair.md` and a state-log entry recording the findings above. | Spec exists and is linked from `CURRENT.md`. |
| 1 | One builder for the host data URI (`app://<package>:<uid>` plus version params), used by the query and the preflight; add `<data android:scheme="app"/>` to the manifest query. | A device test shows the probe for `com.saulhdev.neofeed` returns `com.saulhdev.feeder.manager.service.OverlayService`, and the gateway state is `Available`. |
| 2 | The bind intent carries the same URI. | On device, `bindingEvidence` shows accepted and connected for Neo Feed alone and for Lawnfeed alone. A provider rejection is recorded with its diagnostic cause, not hidden. |
| 3 | Surface diagnostics: a setprop-gated log line for `diagnostic`, `bindingEvidence` and the protocol trace, plus the status and cause in the settings footer (`:app`). | Logcat shows the stage transitions for each provider; with no provider it reads `NO_PROVIDER`. |
| 4 | Correct window handshake: the overlay-enabled option bit, and `windowAttached2` with the bundle when the service advertises a new enough API version. | The trace reaches `sessionConfirmed` (a provider callback is observed) on device for at least one provider. |
| 5 | Forward activity resume and pause through the gateway to the bridge. | Bridge unit tests cover the ordering; on device the session survives home → app → home. |
| 6 | Pager drag sends `startScroll` → `onScroll` → `endScroll`; `openOverlay`/`closeOverlay` are kept for pill navigation only. | Dragging from Home reveals provider content that tracks the finger, with a screenshot in `captures/`. Home gestures (tile drag, vertical scroll) still pass their device suites. |
| 7 | The gateway exposes provider scroll and status as a framework-free flow; the shell syncs the pager to it, and Back closes the overlay. | Closing the feed from inside the provider settles the pager on Home, with a device test or a recorded manual pass. |
| 8 | Provider-neutral discovery: list every service answering the action instead of the allowlist, with identity as package plus component and settings rows built from that list. | All four providers on the device are listed; selecting `amirz.aidlbridge` binds; the priority order is still deterministic. |
| 9 | Device matrix: debug and release, each provider alone and together, live install and removal, force-stop. | Results table in the handoff, each row marked pass, fail or not run with its cause. |

Slices 1–3 are small and low-risk. Slices 4–7 are where real provider behaviour may differ from the reference, which is why slice 3 comes first.

## Decisions for you

- **Keep page 0 as the feed drag surface?** I recommend yes: the provider draws in its own window above a transparent page, and slices 6–7 make the two track each other. The alternative, dropping the page and treating the feed as an edge overscroll like Launcher3, is a larger shell change.
- **Is slice 8 in scope now?** I'd keep it after the feed works, since Lawnfeed and Neo Feed are enough to prove the path. Pull it forward if Lawnfeed rejects this launcher as a host, because the standalone AIDL bridge is then the only route to the Google feed.
- **Neo Feed's "display over other apps" permission is not granted on the device.** I don't think it's needed when drawing against the host's window token, but if slice 4 fails only for Neo Feed, check that first.
