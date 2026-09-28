package me.foxtails.lakehead.platform.feed

/**
 * Visibility of one allowlisted package as seen by a probe.
 *
 * `VISIBLE` means the package answered (installed and visible to the caller);
 * `ABSENT` means the package answered confirmed-absent; `UNKNOWN` means the probe could
 * not tell absence from filtering, so the package must be treated as uncertain — never
 * as proof that nothing is installed.
 */
enum class FeedPackageVisibility {
    VISIBLE,
    ABSENT,
    UNKNOWN,
}

/**
 * One package's answer to the overlay-service probe, in framework-free values.
 *
 * A non-empty [components] list means the package publishes the overlay service: the
 * entries are the exact matching services ([FeedServiceComponent] strings, no
 * `ResolveInfo`), in probe order, and the resolver retains the first. An empty list
 * means no matching service was found, and [packageVisibility] says whether that is a
 * confirmed answer (`VISIBLE` without a service, `ABSENT`) or an inconclusive one
 * (`UNKNOWN`).
 */
data class FeedServiceProbeResult(
    val components: List<FeedServiceComponent>,
    val packageVisibility: FeedPackageVisibility,
) {
    companion object {
        /**
         * The package publishes the overlay service as [components] (first wins).
         *
         * Present implies visible: a service listing is itself the visibility answer.
         */
        fun present(components: List<FeedServiceComponent>): FeedServiceProbeResult {
            require(components.isNotEmpty()) { "present requires at least one component" }
            return FeedServiceProbeResult(
                components = components.toList(),
                packageVisibility = FeedPackageVisibility.VISIBLE,
            )
        }

        /** Single-service convenience for [present]. */
        fun present(component: FeedServiceComponent): FeedServiceProbeResult =
            present(listOf(component))

        /** The package is installed and visible but publishes no matching service. */
        fun visibleWithoutService(): FeedServiceProbeResult = FeedServiceProbeResult(
            components = emptyList(),
            packageVisibility = FeedPackageVisibility.VISIBLE,
        )

        /** The package answered confirmed-absent. */
        fun absent(): FeedServiceProbeResult = FeedServiceProbeResult(
            components = emptyList(),
            packageVisibility = FeedPackageVisibility.ABSENT,
        )

        /** The probe could not tell absence from filtering. */
        fun unknown(): FeedServiceProbeResult = FeedServiceProbeResult(
            components = emptyList(),
            packageVisibility = FeedPackageVisibility.UNKNOWN,
        )
    }
}

/**
 * Seam for asking whether a provider package publishes the feed overlay service.
 *
 * The probe itself is Android work — a `PackageManager` query, and on Android 11+ it needs
 * a matching `<queries>` entry that the app manifest owns. Keeping it behind this interface
 * means [FeedProviderResolver] stays pure and framework-free while the real
 * `PackageManager` implementation lives at the platform boundary
 * ([AndroidFeedServiceQuery]; the gateway owns binding).
 */
fun interface FeedServiceQuery {
    /**
     * Probes [packageName] for a service matching [action].
     *
     * Returns the package's framework-free answer: matching components when present,
     * otherwise the visibility verdict. Implementations map a definitive "not installed"
     * answer to [FeedServiceProbeResult.absent] and an inconclusive one to
     * [FeedServiceProbeResult.unknown]; they never return a bare boolean.
     *
     * Implementations may throw: a platform rejection surfaces as `SecurityException`
     * (classified as [FeedDiagnosticStatus.REJECTED] with the detail redacted) and any
     * other failure as uncertainty (classified as inconclusive, detail redacted).
     * [FeedProviderResolver] classifies rather than propagating.
     */
    fun probe(packageName: String, action: String): FeedServiceProbeResult
}

/**
 * Decides which feed provider, if any, should be hosted.
 *
 * Stateless and free of Android types: the resolver owns the *decision* (which provider
 * wins and how probe outcomes are diagnosed) while [FeedServiceQuery] owns the platform
 * probe. It performs no binding, caching or lifecycle management — those belong to the
 * gateway.
 */
class FeedProviderResolver(private val query: FeedServiceQuery) {

    /**
     * Resolves the provider to host with a full discovery diagnosis.
     *
     * Walks [FeedProviderId.PRIORITY] in order and, within a provider, its application ids
     * in [FeedProviderId.packageNames] order, returning the first package whose probe
     * matches a service. The returned [FeedProviderResolution.target] retains the exact
     * matched [FeedServiceComponent]; [FeedProviderResolution.diagnostic] is null on
     * success. Never throws and never returns a partial result: a failing or
     * inconclusive probe is recorded and the search continues, so a lower-priority
     * confirmed provider wins over an earlier denied, failed or uncertain probe.
     *
     * Unresolved outcomes emit discovery diagnostics only, classified in precedence order:
     *  1. [FeedDiagnosticStatus.REJECTED] — some probe was denied before it could answer,
     *     so absence is unproven (culprit: the first rejected package);
     *  2. [FeedDiagnosticStatus.NOT_DISCOVERABLE] — a visible package publishes no
     *     matching service, or some probe stayed inconclusive (culprit: the first such
     *     package; [FeedDiagnosticCause] and [FeedDiscoveryEvidence] separate the
     *     definitive case from the uncertain one);
     *  3. [FeedDiagnosticStatus.NO_PROVIDER] — every probe answered confirmed-absent,
     *     which is the only unambiguous "nothing installed" outcome.
     */
    fun resolveDetailed(): FeedProviderResolution {
        val probed = mutableListOf<String>()
        val absent = mutableListOf<String>()
        val visibleWithoutService = mutableListOf<String>()
        val uncertain = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val probeFailedPackages = mutableSetOf<String>()

        for (provider in FeedProviderId.PRIORITY) {
            for (packageName in provider.packageNames) {
                probed += packageName
                val result = try {
                    query.probe(packageName, FEED_OVERLAY_ACTION)
                } catch (_: SecurityException) {
                    rejected += packageName
                    continue
                } catch (_: Exception) {
                    uncertain += packageName
                    probeFailedPackages += packageName
                    continue
                }
                val component = result.components.firstOrNull()
                if (component != null) {
                    return FeedProviderResolution(
                        target = FeedProviderTarget(provider, component),
                        diagnostic = null,
                    )
                }
                when (result.packageVisibility) {
                    FeedPackageVisibility.VISIBLE -> visibleWithoutService += packageName
                    FeedPackageVisibility.ABSENT -> absent += packageName
                    FeedPackageVisibility.UNKNOWN -> uncertain += packageName
                }
            }
        }

        return classifyUnresolved(
            probed = probed.toList(),
            absent = absent.toList(),
            visibleWithoutService = visibleWithoutService.toList(),
            uncertain = uncertain.toList(),
            rejected = rejected.toList(),
            probeFailedPackages = probeFailedPackages.toSet(),
        )
    }

    /**
     * Classifies a fully-probed walk that matched no service.
     *
     * Kept separate from the walk itself: the walk owns probe order and early return,
     * this owns the unresolved precedence (rejection before undiscoverable before
     * confirmed absence) and its evidence assembly.
     */
    private fun classifyUnresolved(
        probed: List<String>,
        absent: List<String>,
        visibleWithoutService: List<String>,
        uncertain: List<String>,
        rejected: List<String>,
        probeFailedPackages: Set<String>,
    ): FeedProviderResolution {
        val evidence = FeedDiscoveryEvidence(
            probedPackages = probed,
            absentPackages = absent,
            visibleWithoutService = visibleWithoutService,
            uncertainPackages = uncertain,
            rejectedPackages = rejected,
        )

        if (rejected.isNotEmpty()) {
            val culprit = rejected.first()
            return FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.REJECTED,
                    cause = FeedDiagnosticCause.QUERY_DENIED,
                    provider = providerFor(culprit),
                    packageName = culprit,
                    evidence = evidence,
                ),
            )
        }
        if (visibleWithoutService.isNotEmpty()) {
            val culprit = visibleWithoutService.first()
            return FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.NOT_DISCOVERABLE,
                    cause = FeedDiagnosticCause.NO_MATCHING_SERVICE,
                    provider = providerFor(culprit),
                    packageName = culprit,
                    evidence = evidence,
                ),
            )
        }
        if (uncertain.isNotEmpty()) {
            // Cause travels with the culprit: the first uncertain package's own
            // uncertainty type decides, so an earlier UNKNOWN is never misreported
            // as PROBE_FAILED just because a later probe threw (and vice versa).
            val culprit = uncertain.first()
            return FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.NOT_DISCOVERABLE,
                    cause = if (culprit in probeFailedPackages) {
                        FeedDiagnosticCause.PROBE_FAILED
                    } else {
                        FeedDiagnosticCause.VISIBILITY_UNKNOWN
                    },
                    provider = providerFor(culprit),
                    packageName = culprit,
                    evidence = evidence,
                ),
            )
        }
        return FeedProviderResolution(
            target = null,
            diagnostic = FeedDiagnostic(
                status = FeedDiagnosticStatus.NO_PROVIDER,
                cause = FeedDiagnosticCause.ALL_ABSENT,
                evidence = evidence,
            ),
        )
    }

    /**
     * Resolves the provider to host, or an honest [FeedAvailability.Unavailable].
     *
     * Compatibility projection of [resolveDetailed]: `Available` for the resolved target,
     * `NO_PROVIDER` only when every probe answered confirmed-absent, `PERMISSION_DENIED`
     * when some probe was rejected, `QUERY_FAILED` for every other unresolved outcome.
     * An [FeedAvailability.Available] result always wins over a recorded failure: a
     * provider that answered is evidence enough, even if an earlier probe misbehaved.
     */
    fun resolve(): FeedAvailability = resolveDetailed().availability

    private fun providerFor(packageName: String): FeedProviderId? =
        FeedProviderId.PRIORITY.firstOrNull { packageName in it.packageNames }
}
