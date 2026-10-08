package me.foxtails.lakehead.platform.feed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process

/**
 * Production [FeedServiceQuery]: answers the overlay-service probe from `PackageManager`.
 *
 * This is the only feed-discovery type allowed to touch framework objects. It converts the
 * platform answer into framework-free [FeedServiceProbeResult] values at the boundary:
 * - the package's `getPackageInfo` throws `NameNotFoundException` → confirmed
 *   [FeedServiceProbeResult.absent]. "Confirmed" is relative to manifest visibility: the
 *   app manifest's `<queries>` covers the overlay action with
 *   `<data android:scheme="app" />` matching the host URI, and every allowlisted
 *   provider package (D1 matrix), so a name-not-found there means not installed
 *   rather than filtered. Without that manifest coverage the same answer would be
 *   inconclusive.
 * - the package is visible and `queryIntentServices` lists services for
 *   [FEED_OVERLAY_ACTION] → [FeedServiceProbeResult.present] with the exact matched
 *   services as [FeedServiceComponent] strings (no `ResolveInfo` escapes).
 * - the package is visible but lists none → [FeedServiceProbeResult.visibleWithoutService].
 * - a `SecurityException` from either call propagates so the resolver diagnoses
 *   `REJECTED` with the detail redacted; any other failure propagates so the resolver
 *   diagnoses it as uncertain, also redacted.
 *
 * Holds the application context only, never an activity. Probing never binds: binding
 * stays with the gateway.
 */
class AndroidFeedServiceQuery(private val applicationContext: Context) : FeedServiceQuery {

    override fun probe(packageName: String, action: String): FeedServiceProbeResult {
        val packageManager = applicationContext.packageManager
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return FeedServiceProbeResult.absent()
        }

        val intent = Intent(action).setPackage(packageName)
            .setData(Uri.parse(FeedHostUri.build(applicationContext.packageName, Process.myUid())))
        val services = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        val components = (services ?: emptyList())
            .mapNotNull { it.serviceInfo }
            .map { FeedServiceComponent(packageName = it.packageName, className = it.name) }
            .distinct()
        return if (components.isEmpty()) {
            FeedServiceProbeResult.visibleWithoutService()
        } else {
            FeedServiceProbeResult.present(components)
        }
    }
}
