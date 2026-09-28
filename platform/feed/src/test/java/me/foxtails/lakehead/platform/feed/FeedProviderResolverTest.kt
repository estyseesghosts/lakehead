package me.foxtails.lakehead.platform.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedProviderResolverTest {

    @Test
    fun bothProvidersAvailableResolvesLawnfeedByPriority() {
        val lawnfeedComponent = FeedServiceComponent(LAWNFEED_PACKAGE, "$LAWNFEED_PACKAGE.FeedService")
        val answers = mapOf(
            LAWNFEED_PACKAGE to FeedServiceProbeResult.present(lawnfeedComponent),
            NEO_FEED_PACKAGE to FeedServiceProbeResult.present(
                FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService"),
            ),
        )
        val query = RecordingQuery(answers = answers)

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertEquals(
            FeedProviderTarget(FeedProviderId.Lawnfeed, lawnfeedComponent),
            resolution.target,
        )
        assertNull(resolution.diagnostic)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            resolution.availability,
        )
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            FeedProviderResolver(RecordingQuery(answers = answers)).resolve(),
        )
        // Stops at the first hit: the remaining Lawnfeed alias and Neo Feed are never probed.
        assertEquals(listOf(LAWNFEED_PACKAGE), query.probedPackages)
        assertEquals(listOf(FEED_OVERLAY_ACTION), query.probedActions)
    }

    @Test
    fun onlyNeoFeedAvailableResolvesNeoFeed() {
        val neoComponent = FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.OverlayService")
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.absent(),
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.present(neoComponent),
            ),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertEquals(FeedProviderTarget(FeedProviderId.NeoFeed, neoComponent), resolution.target)
        assertNull(resolution.diagnostic)
        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), resolution.availability)
        assertEquals(
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE, NEO_FEED_PACKAGE),
            query.probedPackages,
        )
    }

    @Test
    fun legacyLawnfeedIdResolvesToLawnfeedWithLegacyComponent() {
        val legacyComponent =
            FeedServiceComponent(LAWNFEED_LEGACY_PACKAGE, "$LAWNFEED_LEGACY_PACKAGE.FeedService")
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.absent(),
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.present(legacyComponent),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.present(
                    FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService"),
                ),
            ),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // Legacy id still resolves — and the retained component is the legacy one, not Neo's.
        assertEquals(FeedProviderTarget(FeedProviderId.Lawnfeed, legacyComponent), resolution.target)
        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), resolution.availability)
        assertEquals(listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE), query.probedPackages)
    }

    @Test
    fun noProviderAnswersReportsNoProvider() {
        val answers = mapOf(
            LAWNFEED_PACKAGE to FeedServiceProbeResult.absent(),
            LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
            NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
        )
        val query = RecordingQuery(answers = answers)

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // NO_PROVIDER only because every allowlisted package answered confirmed-absent.
        assertNull(resolution.target)
        assertEquals(
            FeedDiagnostic(
                status = FeedDiagnosticStatus.NO_PROVIDER,
                cause = FeedDiagnosticCause.ALL_ABSENT,
                evidence = FeedDiscoveryEvidence(
                    probedPackages = ALL_PACKAGES,
                    absentPackages = ALL_PACKAGES,
                ),
            ),
            resolution.diagnostic,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            resolution.availability,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            FeedProviderResolver(RecordingQuery(answers = answers)).resolve(),
        )
        assertEquals(ALL_PACKAGES, query.probedPackages)
    }

    @Test
    fun visiblePackageWithoutServiceReportsNotDiscoverable() {
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.visibleWithoutService(),
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
            ),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // Installed but publishing no service is undiscoverable — never "no provider".
        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.NOT_DISCOVERABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticStage.DISCOVERY, diagnostic?.stage)
        assertEquals(FeedDiagnosticCause.NO_MATCHING_SERVICE, diagnostic?.cause)
        assertEquals(FeedProviderId.Lawnfeed, diagnostic?.provider)
        assertEquals(LAWNFEED_PACKAGE, diagnostic?.packageName)
        assertNull(diagnostic?.component)
        assertEquals(listOf(LAWNFEED_PACKAGE), diagnostic?.evidence?.visibleWithoutService)
        // Coarse projection collapses the distinction to QUERY_FAILED.
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            resolution.availability,
        )
    }

    @Test
    fun uncertainVisibilityIsNotAbsence() {
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.unknown(),
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
            ),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // One inconclusive probe blocks NO_PROVIDER: absence is unproven.
        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.NOT_DISCOVERABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.VISIBILITY_UNKNOWN, diagnostic?.cause)
        assertEquals(listOf(LAWNFEED_PACKAGE), diagnostic?.evidence?.uncertainPackages)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            resolution.availability,
        )
    }

    @Test
    fun probeFailureReportsNotDiscoverableWithRedactedCause() {
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
            ),
            failures = mapOf(LAWNFEED_PACKAGE to { IllegalStateException("pm blowup 7x9q") }),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.NOT_DISCOVERABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.PROBE_FAILED, diagnostic?.cause)
        assertEquals(listOf(LAWNFEED_PACKAGE), diagnostic?.evidence?.uncertainPackages)
        // Redaction: the failure detail never reaches the framework-free diagnostic.
        assertFalse(diagnostic.toString().contains("7x9q"))
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            resolution.availability,
        )
    }

    @Test
    fun earlierUnknownKeepsVisibilityUnknownWhenLaterProbeThrows() {
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.unknown(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
            ),
            failures = mapOf(LAWNFEED_LEGACY_PACKAGE to { IllegalStateException("pm blowup") }),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // Culprit is the first uncertain package; its cause travels with it.
        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.NOT_DISCOVERABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.VISIBILITY_UNKNOWN, diagnostic?.cause)
        assertEquals(LAWNFEED_PACKAGE, diagnostic?.packageName)
        assertEquals(FeedProviderId.Lawnfeed, diagnostic?.provider)
        assertEquals(
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE),
            diagnostic?.evidence?.uncertainPackages,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            resolution.availability,
        )
    }

    @Test
    fun earlierThrownProbeKeepsProbeFailedWhenLaterVisibilityUnknown() {
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.unknown(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.absent(),
            ),
            failures = mapOf(LAWNFEED_PACKAGE to { IllegalStateException("pm blowup") }),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // Culprit is the first uncertain package; its cause travels with it.
        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.NOT_DISCOVERABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.PROBE_FAILED, diagnostic?.cause)
        assertEquals(LAWNFEED_PACKAGE, diagnostic?.packageName)
        assertEquals(FeedProviderId.Lawnfeed, diagnostic?.provider)
        assertEquals(
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE),
            diagnostic?.evidence?.uncertainPackages,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            resolution.availability,
        )
    }

    @Test
    fun securityExceptionOnEveryProbeReportsRejectedWithRedactedCause() {
        val failures = ALL_PACKAGES.associateWith { { SecurityException("not visible s3cr3t") } }
        val query = RecordingQuery(failures = failures)

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertNull(resolution.target)
        val diagnostic = resolution.diagnostic
        assertEquals(FeedDiagnosticStatus.REJECTED, diagnostic?.status)
        assertEquals(FeedDiagnosticStage.DISCOVERY, diagnostic?.stage)
        assertEquals(FeedDiagnosticCause.QUERY_DENIED, diagnostic?.cause)
        assertEquals(FeedProviderId.Lawnfeed, diagnostic?.provider)
        assertEquals(LAWNFEED_PACKAGE, diagnostic?.packageName)
        assertEquals(ALL_PACKAGES, diagnostic?.evidence?.rejectedPackages)
        // Redaction: the SecurityException message never reaches the diagnostic.
        assertFalse(diagnostic.toString().contains("s3cr3t"))
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED),
            resolution.availability,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED),
            FeedProviderResolver(RecordingQuery(failures = failures)).resolve(),
        )
        assertEquals(ALL_PACKAGES, query.probedPackages)
    }

    @Test
    fun rejectionWinsOverVisibleWithoutService() {
        val query = RecordingQuery(
            answers = mapOf(LAWNFEED_PACKAGE to FeedServiceProbeResult.visibleWithoutService()),
            failures = mapOf(NEO_FEED_PACKAGE to { SecurityException("denied") }),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // A denial means absence is unproven even where another package answered visibly.
        assertEquals(FeedDiagnosticStatus.REJECTED, resolution.diagnostic?.status)
        assertEquals(NEO_FEED_PACKAGE, resolution.diagnostic?.packageName)
        assertEquals(FeedProviderId.NeoFeed, resolution.diagnostic?.provider)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED),
            resolution.availability,
        )
    }

    @Test
    fun availableProviderWinsOverEarlierProbeFailure() {
        val neoComponent = FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService")
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.absent(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.present(neoComponent),
            ),
            failures = mapOf(LAWNFEED_PACKAGE to { IllegalStateException("package manager failure") }),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        // Lower-priority confirmed wins over the earlier failed probe.
        assertEquals(FeedProviderTarget(FeedProviderId.NeoFeed, neoComponent), resolution.target)
        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), resolution.availability)
    }

    @Test
    fun availableProviderWinsOverEarlierUncertainVisibility() {
        val neoComponent = FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService")
        val query = RecordingQuery(
            answers = mapOf(
                LAWNFEED_PACKAGE to FeedServiceProbeResult.unknown(),
                LAWNFEED_LEGACY_PACKAGE to FeedServiceProbeResult.unknown(),
                NEO_FEED_PACKAGE to FeedServiceProbeResult.present(neoComponent),
            ),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertEquals(FeedProviderTarget(FeedProviderId.NeoFeed, neoComponent), resolution.target)
        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), resolution.availability)
    }

    @Test
    fun firstMatchingComponentIsRetained() {
        val first = FeedServiceComponent(LAWNFEED_PACKAGE, "$LAWNFEED_PACKAGE.Primary")
        val second = FeedServiceComponent(LAWNFEED_PACKAGE, "$LAWNFEED_PACKAGE.Secondary")
        val query = RecordingQuery(
            answers = mapOf(LAWNFEED_PACKAGE to FeedServiceProbeResult.present(listOf(first, second))),
        )

        val resolution = FeedProviderResolver(query).resolveDetailed()

        assertEquals(FeedProviderTarget(FeedProviderId.Lawnfeed, first), resolution.target)
    }

    @Test
    fun diagnosticStagesCoverAllNineStatuses() {
        assertEquals(
            mapOf(
                FeedDiagnosticStatus.NO_PROVIDER to FeedDiagnosticStage.DISCOVERY,
                FeedDiagnosticStatus.NOT_DISCOVERABLE to FeedDiagnosticStage.DISCOVERY,
                FeedDiagnosticStatus.REJECTED to FeedDiagnosticStage.DISCOVERY,
                FeedDiagnosticStatus.NOT_RESOLVABLE to FeedDiagnosticStage.RESOLUTION,
                FeedDiagnosticStatus.BIND_FAILED to FeedDiagnosticStage.BIND,
                FeedDiagnosticStatus.DISCONNECTED to FeedDiagnosticStage.CONNECTION,
                FeedDiagnosticStatus.NEGOTIATION_FAILED to FeedDiagnosticStage.NEGOTIATION,
                FeedDiagnosticStatus.SURFACE_SESSION_FAILED to FeedDiagnosticStage.SURFACE_SESSION,
                FeedDiagnosticStatus.RENDERING_FAILED to FeedDiagnosticStage.RENDERING,
            ),
            FeedDiagnosticStatus.entries.associateWith { it.stage },
        )
        assertEquals(9, FeedDiagnosticStatus.entries.size)
    }

    @Test
    fun resolutionAvailabilityDerivesFromTargetAndDiagnostic() {
        val target = FeedProviderTarget(
            FeedProviderId.NeoFeed,
            FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService"),
        )
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            FeedProviderResolution(target = target, diagnostic = null).availability,
        )
        assertTrue(
            FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.NOT_DISCOVERABLE,
                    cause = FeedDiagnosticCause.NO_MATCHING_SERVICE,
                ),
            ).availability is FeedAvailability.Unavailable,
        )
    }

    private companion object {
        val ALL_PACKAGES: List<String> = listOf(
            LAWNFEED_PACKAGE,
            LAWNFEED_LEGACY_PACKAGE,
            NEO_FEED_PACKAGE,
        )
    }
}

/**
 * Fake [FeedServiceQuery] with a scripted [FeedServiceProbeResult] per package, recording
 * every probe.
 *
 * Packages in [failures] throw instead of answering, so a test can prove both that a
 * failing probe is classified and that resolution continues afterwards. Packages with no
 * scripted answer report [FeedServiceProbeResult.unknown], forcing tests to be explicit
 * about confirmed absence ([FeedServiceProbeResult.absent]) wherever `NO_PROVIDER`
 * depends on it.
 */
private class RecordingQuery(
    private val answers: Map<String, FeedServiceProbeResult> = emptyMap(),
    private val failures: Map<String, () -> Exception> = emptyMap(),
) : FeedServiceQuery {

    val probedPackages: MutableList<String> = mutableListOf()

    val probedActions: MutableList<String> = mutableListOf()

    override fun probe(packageName: String, action: String): FeedServiceProbeResult {
        probedPackages += packageName
        probedActions += action
        failures[packageName]?.let { throw it() }
        return answers[packageName] ?: FeedServiceProbeResult.unknown()
    }
}
