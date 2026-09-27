package me.foxtails.lakehead.platform.shortcuts

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import me.foxtails.lakehead.core.model.ProfileId

/**
 * Coarsens platform shortcut and package changes into gateway refresh requests.
 * A shortcut change requeries only the changed package in the changed profile;
 * a package change coarsely refreshes every cached identity for that package;
 * a package removal drops only that package's entries in the removed profile
 * so the same package in another profile keeps its snapshot. Profile mapping
 * failures are dropped: an inaccessible profile can never name a refresh or
 * removal target.
 */
internal class ShortcutInvalidationSource(
    private val profileIdFor: (UserHandle) -> ProfileId?,
    private val onShortcutsChanged: (packageName: String, profileId: ProfileId) -> Unit,
    private val onPackageChanged: (packageName: String) -> Unit,
    private val onPackageRemoved: (packageName: String, profileId: ProfileId) -> Unit,
) : LauncherApps.Callback() {
    override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {
        val profileId = profileIdFor(user) ?: return
        onShortcutsChanged(packageName, profileId)
    }

    override fun onPackageAdded(packageName: String, user: UserHandle) = Unit

    override fun onPackageChanged(packageName: String, user: UserHandle) = onPackageChanged(packageName)

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        val profileId = profileIdFor(user) ?: return
        onPackageRemoved(packageName, profileId)
    }

    override fun onPackagesAvailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = Unit

    override fun onPackagesUnavailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = Unit
}
