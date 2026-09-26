package me.foxtails.lakehead.platform.feed

/**
 * Seam for asking whether a provider package publishes the feed overlay service.
 *
 * The probe itself is Android work — a `PackageManager` query, and on Android 11+ it needs a
 * matching `<queries>` entry that the app manifest owns. Keeping it behind this interface means
 * [FeedProviderResolver] stays pure and framework-free while the real `PackageManager`
 * implementation lives at the platform boundary (the gateway that also owns binding).
 */
fun interface FeedServiceQuery {
    /**
     * Returns true when [packageName] publishes a service matching [action].
     *
     * Implementations may throw: a missing `<queries>` entry, a revoked
     * `QUERY_ALL_PACKAGES`-style restriction or a package-manager failure all surface as
     * exceptions here, and [FeedProviderResolver] classifies them rather than propagating them.
     */
    fun isServiceAvailable(packageName: String, action: String): Boolean
}

/**
 * Decides which feed provider, if any, should be hosted.
 *
 * Stateless and free of Android types: the resolver owns the *decision* (which provider wins
 * and how query failures are reported) while [FeedServiceQuery] owns the platform probe. It
 * performs no binding, caching or lifecycle management — those belong to the gateway.
 */
class FeedProviderResolver(private val query: FeedServiceQuery) {

    /**
     * Resolves the provider to host, or an honest [FeedAvailability.Unavailable].
     *
     * Walks [FeedProviderId.PRIORITY] in order and, within a provider, its application ids in
     * [FeedProviderId.packageNames] order, returning [FeedAvailability.Available] for the first
     * package that publishes [FEED_OVERLAY_ACTION]. Never throws and never returns a partial
     * result: a failing probe is classified and the search continues with the next id.
     *
     * Failure classification when nothing is available, in precedence order:
     *  1. [FeedAvailability.Unavailable.Reason.PERMISSION_DENIED] — some probe was rejected
     *     before it could answer, so the absence of a provider is not proven;
     *  2. [FeedAvailability.Unavailable.Reason.QUERY_FAILED] — some probe failed for another
     *     reason, with the same uncertainty;
     *  3. [FeedAvailability.Unavailable.Reason.NO_PROVIDER] — every probe answered and none
     *     matched, which is the only unambiguous "nothing installed" outcome.
     *
     * An [FeedAvailability.Available] result always wins over a recorded failure: a provider
     * that answered is evidence enough, even if an earlier probe misbehaved.
     */
    fun resolve(): FeedAvailability {
        var permissionDenied = false
        var queryFailed = false

        for (provider in FeedProviderId.PRIORITY) {
            for (packageName in provider.packageNames) {
                val available = try {
                    query.isServiceAvailable(packageName, FEED_OVERLAY_ACTION)
                } catch (_: SecurityException) {
                    permissionDenied = true
                    false
                } catch (_: Exception) {
                    queryFailed = true
                    false
                }
                if (available) return FeedAvailability.Available(provider)
            }
        }

        val reason = when {
            permissionDenied -> FeedAvailability.Unavailable.Reason.PERMISSION_DENIED
            queryFailed -> FeedAvailability.Unavailable.Reason.QUERY_FAILED
            else -> FeedAvailability.Unavailable.Reason.NO_PROVIDER
        }
        return FeedAvailability.Unavailable(reason)
    }
}
