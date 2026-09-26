package me.foxtails.lakehead.platform.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedProviderResolverTest {

    @Test
    fun bothProvidersAvailableResolvesLawnfeedByPriority() {
        val query = RecordingQuery(available = ALL_PACKAGES)

        val result = FeedProviderResolver(query).resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), result)
        // Stops at the first hit: the remaining Lawnfeed alias and Neo Feed are never probed.
        assertEquals(listOf(LAWNFEED_PACKAGE), query.probedPackages)
        assertEquals(listOf(FEED_OVERLAY_ACTION), query.probedActions)
    }

    @Test
    fun onlyNeoFeedAvailableResolvesNeoFeed() {
        val query = RecordingQuery(available = setOf(NEO_FEED_PACKAGE))

        val result = FeedProviderResolver(query).resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), result)
        assertEquals(
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE, NEO_FEED_PACKAGE),
            query.probedPackages,
        )
    }

    @Test
    fun legacyLawnfeedIdResolvesToLawnfeed() {
        val query = RecordingQuery(available = setOf(LAWNFEED_LEGACY_PACKAGE, NEO_FEED_PACKAGE))

        val result = FeedProviderResolver(query).resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), result)
        assertEquals(listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE), query.probedPackages)
    }

    @Test
    fun noProviderAnswersReportsNoProvider() {
        val query = RecordingQuery(available = emptySet())

        val result = FeedProviderResolver(query).resolve()

        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            result,
        )
        assertEquals(ALL_PACKAGES, query.probedPackages)
    }

    @Test
    fun securityExceptionOnEveryProbeReportsPermissionDenied() {
        val query = RecordingQuery(
            available = emptySet(),
            failure = ALL_PACKAGES,
        ) { SecurityException("not visible") }

        val result = FeedProviderResolver(query).resolve()

        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED),
            result,
        )
        assertEquals(ALL_PACKAGES, query.probedPackages)
    }

    @Test
    fun availableProviderWinsOverEarlierProbeFailure() {
        val query = RecordingQuery(
            available = setOf(NEO_FEED_PACKAGE),
            failure = setOf(LAWNFEED_PACKAGE),
        ) { IllegalStateException("package manager failure") }

        val result = FeedProviderResolver(query).resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), result)
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
 * Fake [FeedServiceQuery] with a scripted answer per package, recording every probe.
 *
 * Packages in [failure] throw the [failureFor] exception instead of answering, so a test can
 * prove both that a failing probe is classified and that resolution continues afterwards.
 */
private class RecordingQuery(
    private val available: Collection<String>,
    private val failure: Collection<String> = emptySet(),
    private val failureFor: (String) -> Exception = { RuntimeException("query failed") },
) : FeedServiceQuery {

    val probedPackages: MutableList<String> = mutableListOf()

    val probedActions: MutableList<String> = mutableListOf()

    override fun isServiceAvailable(packageName: String, action: String): Boolean {
        probedPackages += packageName
        probedActions += action
        if (packageName in failure) throw failureFor(packageName)
        return packageName in available
    }
}
