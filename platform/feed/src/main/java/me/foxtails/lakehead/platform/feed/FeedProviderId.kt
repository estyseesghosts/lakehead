package me.foxtails.lakehead.platform.feed

/**
 * Intent action published by Launcher3-style feed overlay services.
 *
 * Lawnfeed, Lawnchair Discover and Neo Feed all bind their feed UI as a service under this
 * action, so it is a *provider* contract rather than a ProtoLaunch framework constant. It is
 * spelled out in pure Kotlin here; the Android `Intent` that carries it is built later, at the
 * platform boundary, by the gateway that owns binding.
 */
const val FEED_OVERLAY_ACTION: String = "com.android.launcher3.WINDOW_OVERLAY"

/** Current Lawnfeed application id (`app.lawnchair.lawnfeed`). */
const val LAWNFEED_PACKAGE: String = "app.lawnchair.lawnfeed"

/** Pre-rename Lawnfeed application id (`ch.deletescape.lawnchair.lawnfeed`). */
const val LAWNFEED_LEGACY_PACKAGE: String = "ch.deletescape.lawnchair.lawnfeed"

/** Neo Feed application id (`com.saulhdev.neofeed`). */
const val NEO_FEED_PACKAGE: String = "com.saulhdev.neofeed"

/**
 * A known third-party feed provider, identified by the application ids it may ship under.
 *
 * Only stable strings live here: no `ComponentName`, `Intent`, `ServiceConnection` or other
 * framework type, so this identity can be held in domain-facing state, persisted or passed
 * across a module edge unchanged. Whether a provider is actually installed is *not* part of
 * the identity — that is [FeedAvailability].
 */
sealed interface FeedProviderId {

    /**
     * Application ids to probe, in probe order.
     *
     * The first entry is the provider's current application id; any further entry is a legacy
     * id still shipped by older installs, so an un-migrated provider is still discovered.
     */
    val packageNames: List<String>

    /** Lawnfeed — the Google feed companion for Lawnchair-style launchers. */
    data object Lawnfeed : FeedProviderId {
        override val packageNames: List<String> =
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE)
    }

    /** Neo Feed — a Discover-style feed provider. */
    data object NeoFeed : FeedProviderId {
        override val packageNames: List<String> = listOf(NEO_FEED_PACKAGE)
    }

    companion object {
        /**
         * Provider preference order, highest first: **Lawnfeed > Neo Feed**.
         *
         * Lawnfeed wins because it is the provider this launcher ships against; Neo Feed is the
         * fallback. Resolution therefore walks this list in order and stops at the first
         * provider that answers, so with both installed the choice is deterministic and stable
         * rather than dependent on query order, install age or `PackageManager` iteration order.
         */
        val PRIORITY: List<FeedProviderId> = listOf(Lawnfeed, NeoFeed)
    }
}
