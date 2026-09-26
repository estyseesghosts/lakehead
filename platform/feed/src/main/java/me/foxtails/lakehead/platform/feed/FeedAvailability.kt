package me.foxtails.lakehead.platform.feed

/**
 * Whether a feed provider can be hosted right now.
 *
 * This is the only feed fact a presentation layer needs: an [Available] value names the
 * provider to host, and an [Unavailable] value carries a truthful reason instead of a silent
 * empty state. It holds no framework types, so it can be published as state and collected in
 * a composable without leaking `Context` or `ServiceConnection`.
 */
sealed interface FeedAvailability {

    /**
     * A known provider answered the overlay-service query.
     *
     * [provider] is the identity to bind, already chosen by [FeedProviderId.PRIORITY] order.
     * It does **not** promise the bind will succeed: an installed provider can still be
     * disabled between this resolution and the bind attempt.
     */
    data class Available(val provider: FeedProviderId) : FeedAvailability

    /**
     * No provider could be hosted, and [reason] says which failure was observed.
     *
     * The reason distinguishes "nothing is installed" from "the query was not permitted" and
     * "the query itself broke", because only the first is a normal, expected state.
     */
    data class Unavailable(val reason: Reason) : FeedAvailability {

        /** Why no provider is available. */
        enum class Reason {
            /** No known provider answered the query. */
            NO_PROVIDER,

            /** The query was rejected by the platform before it could complete. */
            PERMISSION_DENIED,

            /** The query failed for a reason other than a permission rejection. */
            QUERY_FAILED,
        }
    }
}
