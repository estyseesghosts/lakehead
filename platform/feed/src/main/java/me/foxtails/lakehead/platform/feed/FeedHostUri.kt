package me.foxtails.lakehead.platform.feed

/**
 * Builds the host data URI that feed overlay providers expect on query and bind intents.
 *
 * The [FEED_OVERLAY_ACTION] intent-filter declared by providers (Lawnfeed, Neo Feed and
 * others) includes a `scheme="app"` data specification, so discovery and binding intents
 * that carry no data never match. This builder produces the URI that completes the filter.
 *
 * Format and constants are derived from Lawnchair's `LauncherClient.getIntent` at
 * commit `505dbc40e6154c05158b5d0271c45f6a885a411b`: action
 * `com.android.launcher3.WINDOW_OVERLAY`, data `app://<hostPackage>:<hostUid>?v=7&cv=9`,
 * where `v` is the overlay API version (7) and `cv` is the client version (9).
 * Provider intent-filters match on scheme `"app"`.
 */
object FeedHostUri {

    /** URI scheme expected by overlay provider intent-filters. */
    const val SCHEME: String = "app"

    /** Query-parameter name for the overlay API version. */
    private const val PARAM_VERSION: String = "v"

    /** Query-parameter name for the client version. */
    private const val PARAM_CLIENT_VERSION: String = "cv"

    /** Overlay API version (matches LauncherClient.getIntent at the cited commit). */
    private const val VERSION: Int = 7

    /** Client version (matches LauncherClient.getIntent at the cited commit). */
    private const val CLIENT_VERSION: Int = 9

    /**
     * Builds `app://<hostPackage>:<hostUid>?v=7&cv=9`.
     *
     * @param hostPackage the host application's package name; must be non-blank.
     * @param hostUid the host process uid (typically [android.os.Process.myUid()]); must be
     *   non-negative.
     * @return the complete data URI string for the overlay query and bind intents.
     * @throws IllegalArgumentException if [hostPackage] is blank or [hostUid] is negative.
     */
    fun build(hostPackage: String, hostUid: Int): String {
        require(hostPackage.isNotBlank()) { "hostPackage must not be blank" }
        require(hostUid >= 0) { "hostUid must be non-negative, was $hostUid" }
        return "$SCHEME://$hostPackage:$hostUid?$PARAM_VERSION=$VERSION&$PARAM_CLIENT_VERSION=$CLIENT_VERSION"
    }
}
