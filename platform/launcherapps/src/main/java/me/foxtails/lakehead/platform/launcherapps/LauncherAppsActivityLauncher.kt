package me.foxtails.lakehead.platform.launcherapps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import me.foxtails.lakehead.core.model.AppIdentity

/** Uses LauncherApps to launch a component in exactly the accessible profile named by identity. */
class LauncherAppsActivityLauncher(context: Context) : AppLauncher {
    private val launcherApps: LauncherApps
    private val profileTokens: ProfileTokenAdapter

    init {
        val applicationContext = requireNotNull(context.applicationContext)
        launcherApps = requireNotNull(
            applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps,
        )
        val userManager = requireNotNull(
            applicationContext.getSystemService(Context.USER_SERVICE) as? UserManager,
        )
        profileTokens = ProfileTokenAdapter(launcherApps, userManager)
    }

    override fun launch(identity: AppIdentity): Boolean {
        val user = profileTokens.userFor(identity.profileId) ?: return false
        val component = ComponentName(identity.component.packageName, identity.component.className)

        return try {
            val isLaunchableForProfile = launcherApps
                .getActivityList(identity.component.packageName, user)
                .any { it.componentName == component }
            if (!isLaunchableForProfile) return false

            launcherApps.startMainActivity(component, user, null, null)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
