# Feed overlay repair

Owner: `:platform:feed` (discovery, bind, protocol, diagnostics); `:app` wiring only (manifest `<queries>`, settings footer). Source: `docs/fixfeed.md`. Reference client: Lawnchair `LauncherClient.java` at commit `505dbc40e6154c05158b5d0271c45f6a885a411b` (branch `15-dev`); the vendored AIDL provenance is recorded in `AidlFeedBridge.kt`.

## Contract

1. **Host data URI (slice 1).** One framework-free builder (`FeedHostUri`) produces `app://<hostPackage>:<uid>?v=7&cv=9` — the exact form of the reference `LauncherClient.getIntent` (`Process.myUid()`, `v=7` overlay API version, `cv=9` client version). The discovery query (`AndroidFeedServiceQuery.probe`) and the bind preflight (`AndroidFeedPreflight.verify`) set it as the intent data alongside `setPackage(provider)`; the app manifest `<queries>` WINDOW_OVERLAY entry declares `<data android:scheme="app"/>`. The bind intent is unchanged (slice 2).
2. **Bind carries the URI (slice 2).** `AndroidFeedConnector.bind` sets the same host data URI on the explicit-component bind intent. `bindingEvidence` records acceptance vs connection separately; a provider rejection is recorded with its diagnostic cause, never hidden.
3. **Surface diagnostics (slice 3).** A setprop-gated log line emits `diagnostic`, `bindingEvidence` and the protocol trace transitions; the settings footer shows status + cause (`:app`). With no provider installed it reads `NO_PROVIDER`.

Slices 4–9 (window handshake, lifecycle, scroll verbs, provider callbacks, provider-neutral discovery, device matrix) are defined in `docs/fixfeed.md` and out of scope here; before slices 4–6 the implementer must re-check flags and call order against the reference `LauncherClient` at the pinned commit.

## Acceptance

- Unit (`:platform:feed:testDebugUnitTest`): `FeedHostUriTest` pins the exact string `app://me.foxtails.lakehead:10000?v=7&cv=9` plus rejection of blank package / negative uid; existing `FeedProviderResolverTest`, `FeedOverlayGatewayTest`, `AidlFeedBridgeTest` still pass.
- Device (needs a device with providers installed): probe for `com.saulhdev.neofeed` returns `com.saulhdev.feeder.manager.service.OverlayService` and gateway state is `Available` (slice 1); `bindingEvidence` shows accepted + connected for Neo Feed alone and Lawnfeed alone (slice 2); logcat shows stage transitions per provider, `NO_PROVIDER` with none installed (slice 3).
- AGENTS.md §6 validation per slice; each slice ends with a state-log handoff.
