package me.foxtails.lakehead.platform.launcherapps

import android.os.UserHandle
import android.os.UserManager
import android.content.pm.LauncherApps
import me.foxtails.lakehead.core.model.ProfileId

/** Converts platform profile handles to stable opaque domain tokens at the Android boundary. */
internal class ProfileTokenAdapter(
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
) {
    fun profileIdFor(user: UserHandle): ProfileId? {
        if (user !in launcherApps.profiles) return null
        return try {
            val serial = userManager.getSerialNumberForUser(user)
            if (serial < 0L) null else ProfileId(serial.toString())
        } catch (_: SecurityException) {
            null
        }
    }

    fun userFor(profileId: ProfileId): UserHandle? {
        val serial = profileId.value.toLongOrNull() ?: return null
        if (serial < 0L || serial.toString() != profileId.value) return null
        return try {
            userManager.getUserForSerialNumber(serial)?.takeIf { it in launcherApps.profiles }
        } catch (_: SecurityException) {
            null
        }
    }
}
