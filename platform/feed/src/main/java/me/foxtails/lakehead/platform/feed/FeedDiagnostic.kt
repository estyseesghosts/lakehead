package me.foxtails.lakehead.platform.feed

/**
 * D2 discovery/validation + diagnostic contract for feed hosting.
 *
 * Framework-free and transient: only stable strings, provider identities and allow-listed
 * cause codes live here — no `ComponentName`, `Intent`, `ServiceConnection`, `Throwable` or
 * exception text — so a diagnostic can be held in state, logged and passed across a module
 * edge without leaking framework objects or redacted failure detail.
 *
 * The contract distinguishes nine pipeline states, each bound to the stage that owns it:
 * discovery (`NO_PROVIDER`, `NOT_DISCOVERABLE`, `REJECTED`), resolution (`NOT_RESOLVABLE`),
 * bind (`BIND_FAILED`), connection (`DISCONNECTED`), negotiation (`NEGOTIATION_FAILED`),
 * surface session (`SURFACE_SESSION_FAILED`) and rendering (`RENDERING_FAILED`).
 *
 * D2 wires discovery only: [FeedProviderResolver.resolveDetailed] emits the three
 * discovery states and retains the exact resolved component in [FeedProviderTarget] on
 * success. The remaining six states are owned by later slices (D3 binding/binder lifecycle,
 * D4 AIDL/surface/render trace, D5 wiring) and exist here so the API they report through
 * is frozen before UI work begins.
 */

/**
 * One installed overlay service, identified by stable strings only.
 *
 * Mirrors `ResolveInfo.serviceInfo.packageName` + `serviceInfo.name` without holding a
 * `ComponentName`, so the exact service the resolver matched can be retained in
 * [FeedProviderTarget], persisted or compared in tests on a plain JVM.
 */
data class FeedServiceComponent(
    val packageName: String,
    val className: String,
)

/**
 * The provider chosen by resolution together with the exact service to bind.
 *
 * Carries the [FeedServiceComponent] the probe matched — not just the [provider] identity —
 * so binding later addresses the discovered service instead of re-guessing it.
 */
data class FeedProviderTarget(
    val provider: FeedProviderId,
    val component: FeedServiceComponent,
)

/**
 * Pipeline stage that owns a [FeedDiagnosticStatus].
 *
 * Discovery and resolution belong to [FeedProviderResolver]; bind, connection, negotiation,
 * surface session and rendering belong to the gateway/protocol slices (D3-D5).
 */
enum class FeedDiagnosticStage {
    DISCOVERY,
    RESOLUTION,
    BIND,
    CONNECTION,
    NEGOTIATION,
    SURFACE_SESSION,
    RENDERING,
}

/**
 * The nine feed-hosting outcomes, each bound to its owning stage.
 *
 * D2 emits only the discovery states; the rest are declared here so D3-D5 report through
 * the same [FeedDiagnostic] shape:
 * - [NO_PROVIDER]: every allowlisted package answered confirmed-absent. The only
 *   unambiguous "nothing installed" outcome.
 * - [NOT_DISCOVERABLE]: no provider could be discovered — an installed, visible package
 *   publishes no matching service, or probe visibility was inconclusive. [FeedDiagnosticCause]
 *   and [FeedDiscoveryEvidence] separate the definitive case from the uncertain one.
 * - [REJECTED]: a probe was denied by the platform (`SecurityException`, redacted).
 * - [NOT_RESOLVABLE]: reserved for the resolution step (D3+ ownership).
 * - [BIND_FAILED]: reserved for the bind step (D3 ownership).
 * - [DISCONNECTED]: reserved for transport loss (D3 ownership).
 * - [NEGOTIATION_FAILED]: the AIDL handshake broke before any provider callback
 *   (D4 ownership, via [FeedProtocolTrace] + [AidlFeedBridge]).
 * - [SURFACE_SESSION_FAILED]: the window/session handshake broke (D4 ownership,
 *   via [FeedProtocolTrace] + [AidlFeedBridge]).
 * - [RENDERING_FAILED]: explicit provider no-content signal after a confirmed
 *   session (D4 ownership, via [FeedProtocolTrace] + [AidlFeedBridge]).
 */
enum class FeedDiagnosticStatus(val stage: FeedDiagnosticStage) {
    NO_PROVIDER(FeedDiagnosticStage.DISCOVERY),
    NOT_DISCOVERABLE(FeedDiagnosticStage.DISCOVERY),
    REJECTED(FeedDiagnosticStage.DISCOVERY),
    NOT_RESOLVABLE(FeedDiagnosticStage.RESOLUTION),
    BIND_FAILED(FeedDiagnosticStage.BIND),
    DISCONNECTED(FeedDiagnosticStage.CONNECTION),
    NEGOTIATION_FAILED(FeedDiagnosticStage.NEGOTIATION),
    SURFACE_SESSION_FAILED(FeedDiagnosticStage.SURFACE_SESSION),
    RENDERING_FAILED(FeedDiagnosticStage.RENDERING),
}

/**
 * Allow-listed, safe cause for a [FeedDiagnostic].
 *
 * Never carries exception text, a stack trace or a `Throwable`: probes that throw are
 * classified into one of these codes and the original failure is dropped, so logging a
 * diagnostic cannot leak redacted detail. D2 defines the discovery causes; D3 adds the
 * resolution/bind/connection causes for its own stages rather than reusing a discovery
 * cause. In particular a bind-time `SecurityException` is [BIND_DENIED] (stage BIND),
 * never the discovery [QUERY_DENIED] (stage DISCOVERY).
 */
enum class FeedDiagnosticCause {
    /** Every allowlisted package answered confirmed-absent; no failure occurred. */
    ALL_ABSENT,

    /** An installed, visible package publishes no matching overlay service. */
    NO_MATCHING_SERVICE,

    /** The platform denied the probe; the `SecurityException` detail is redacted. */
    QUERY_DENIED,

    /** Probe visibility was inconclusive, so absence is unproven. */
    VISIBILITY_UNKNOWN,

    /** The probe failed for a non-security reason; the failure detail is redacted. */
    PROBE_FAILED,

    /** Preflight miss: the resolved exact component no longer publishes the service. */
    TARGET_NOT_RESOLVABLE,

    /** Preflight denied by the platform; resolvability is unknown, detail redacted. */
    RESOLUTION_DENIED,

    /** Preflight failed for a non-security reason; resolvability unknown, redacted. */
    RESOLUTION_FAILED,

    /** `bindService` returned false: the bind was declined, not connected. */
    BIND_DECLINED,

    /** `bindService` threw `SecurityException`; detail redacted, never REJECTED. */
    BIND_DENIED,

    /** `bindService` threw for a non-security reason; detail redacted. */
    BIND_ERROR,

    /** `onServiceConnected` named a package/class other than the attempted target. */
    UNEXPECTED_COMPONENT,

    /** `onNullBinding` arrived: the service returned a null binder. */
    NULL_BINDING,

    /** A matching connection later reported `onServiceDisconnected`. */
    SERVICE_DISCONNECTED,

    /** A matching connection later reported `onBindingDied` or binder death. */
    BINDER_DIED,

    /**
     * A control transact (`openOverlay`, `closeOverlay`, `onScroll`) failed before any
     * provider callback was ever observed: the AIDL negotiation itself broke, not an
     * established session. D4 ownership; reported with
     * [FeedDiagnosticStatus.NEGOTIATION_FAILED]. A failure after a callback was
     * observed is transport loss on a live session (`DISCONNECTED`), never this.
     */
    CONTROL_TRANSACT_FAILED,

    /**
     * The `windowAttached` transact failed: the host window was never registered, so
     * the provider holds no session. D4 ownership; reported with
     * [FeedDiagnosticStatus.SURFACE_SESSION_FAILED]. A completed `windowAttached`
     * transact is only "signal sent" (oneway, no acknowledgement) — session proof
     * requires an observed provider callback, tracked in [FeedProtocolTrace].
     */
    WINDOW_TRANSACT_FAILED,

    /**
     * The provider explicitly reported it holds no overlay content
     * (`hasOverlayContent() == false`) after a session was confirmed by an observed
     * callback. D4 ownership; reported with [FeedDiagnosticStatus.RENDERING_FAILED].
     * Only an explicit provider signal reports this — absence of a signal is
     * "unconfirmed", never a failure, and the host surface tag is never render proof.
     */
    CONTENT_ABSENT,
}

/**
 * Resolvability of one [FeedProviderTarget] as seen by the bind preflight.
 *
 * Framework-free so the gateway can record it in [FeedBindingEvidence] and tests can
 * script it without touching `PackageManager`: `RESOLVABLE` means the exact component
 * still publishes the overlay service, `NOT_RESOLVABLE` means it verifiably does not,
 * and the `UNKNOWN_*` values mean the check itself could not answer (denied vs failed).
 */
enum class FeedTargetResolvability {
    RESOLVABLE,
    NOT_RESOLVABLE,
    UNKNOWN_DENIED,
    UNKNOWN_FAILED,
}

/**
 * Per-attempt binding evidence for the current (or most recent) bind.
 *
 * Every field is a stable string, boolean or enum — no framework objects, no binder,
 * no throwable text — so the snapshot can be held in state and logged. The fields
 * separate the four D3 steps: [attempted] (a bind was tried for [target] at [epoch]),
 * [resolvability] (preflight verdict), [accepted] (`bindService` true — acceptance,
 * never proof of a binder), [connected] (a matching binder was installed and
 * correlated with the acceptance), [active] (the bridge is currently live).
 * `accepted && !connected` is the expected transient after a successful bind and
 * before the matching `onServiceConnected`; it is not a failure and carries no new
 * status or timeout.
 */
data class FeedBindingEvidence(
    val target: FeedProviderTarget? = null,
    val attempted: Boolean = false,
    val resolvability: FeedTargetResolvability? = null,
    val accepted: Boolean = false,
    val connected: Boolean = false,
    val active: Boolean = false,
    val epoch: Long = 0,
)

/**
 * Per-package visibility evidence behind a discovery diagnostic.
 *
 * Every probed allowlisted package lands in exactly one bucket, so `NO_PROVIDER` is
 * provable (all buckets except [absentPackages] empty) and uncertainty is explicit
 * instead of being reported as absence.
 */
data class FeedDiscoveryEvidence(
    /** Allowlisted packages probed, in probe order. */
    val probedPackages: List<String> = emptyList(),
    /** Packages that answered confirmed-absent. */
    val absentPackages: List<String> = emptyList(),
    /** Installed, visible packages publishing no matching service. */
    val visibleWithoutService: List<String> = emptyList(),
    /** Packages whose visibility was inconclusive or whose probe failed. */
    val uncertainPackages: List<String> = emptyList(),
    /** Packages whose probe was denied by the platform. */
    val rejectedPackages: List<String> = emptyList(),
)

/**
 * One diagnosed feed-hosting outcome.
 *
 * [status] names the outcome and [stage] (derived from [status]) names its owning step;
 * [provider]/[packageName]/[component] identify the subject when one exists (a failure
 * diagnostic never invents a component — only [FeedProviderTarget] on success retains
 * one); [cause] is the allow-listed safe reason; [evidence] carries the discovery
 * buckets for discovery states and is null for later stages unless their owner defines it.
 */
data class FeedDiagnostic(
    val status: FeedDiagnosticStatus,
    val cause: FeedDiagnosticCause,
    val provider: FeedProviderId? = null,
    val packageName: String? = null,
    val component: FeedServiceComponent? = null,
    val evidence: FeedDiscoveryEvidence? = null,
) {
    /** Owning pipeline step, derived from [status] so the two can never disagree. */
    val stage: FeedDiagnosticStage get() = status.stage
}

/**
 * Discovery verdict: the resolved bind target plus its diagnosis and coarse projection.
 *
 * Success carries a non-null [target] with a null [diagnostic] — there is no failure to
 * diagnose. An unresolved outcome carries a null [target] with a non-null [diagnostic]
 * (D2: one of the three discovery states). [availability] is the coarse
 * [FeedAvailability] projection presentation layers consume:
 * - non-null [target] → `Available(target.provider)`;
 * - `NO_PROVIDER` → `Unavailable(NO_PROVIDER)`;
 * - `REJECTED` → `Unavailable(PERMISSION_DENIED)`;
 * - every other unresolved outcome (including `NOT_DISCOVERABLE`, whether definitive or
 *   uncertain) → `Unavailable(QUERY_FAILED)`.
 */
data class FeedProviderResolution(
    val target: FeedProviderTarget?,
    val diagnostic: FeedDiagnostic?,
) {
    /** Coarse presentation projection derived from [target] and [diagnostic]. */
    val availability: FeedAvailability
        get() {
            val resolved = target
            if (resolved != null) return FeedAvailability.Available(resolved.provider)
            return when (diagnostic?.status) {
                FeedDiagnosticStatus.NO_PROVIDER ->
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER)
                FeedDiagnosticStatus.REJECTED ->
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED)
                else ->
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED)
            }
        }
}
