package me.foxtails.lakehead.platform.launcherapps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Coarsens profile-lifecycle broadcasts into a request to refresh the full inventory.
 *
 * Direct receiver invocation only verifies action mapping. It does not prove whether or how the
 * system delivers these broadcasts to a launcher, or which profiles will be visible there.
 */
internal class ProfileLifecycleInvalidationReceiver(
    private val invalidate: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action in supportedActions) invalidate()
    }

    internal companion object {
        internal val supportedActions: Set<String> = mutableSetOf(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Intent.ACTION_PROFILE_ADDED)
                add(Intent.ACTION_PROFILE_REMOVED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Intent.ACTION_PROFILE_AVAILABLE)
                add(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
    }
}
