package me.foxtails.lakehead.platform.launcherapps

import android.content.pm.LauncherApps
import android.os.UserHandle

/** Coarsens launcher package availability/suspension changes into a full inventory refresh request. */
internal class LauncherAppsInvalidationCallback(
    private val invalidate: () -> Unit,
) : LauncherApps.Callback() {
    override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate()

    override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidate()

    override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate()

    override fun onPackagesAvailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = invalidate()

    override fun onPackagesUnavailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = invalidate()

    override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = invalidate()

    override fun onPackagesSuspended(
        packageNames: Array<out String>,
        user: UserHandle,
        launcherExtras: android.os.Bundle?,
    ) = invalidate()

    override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = invalidate()
}
