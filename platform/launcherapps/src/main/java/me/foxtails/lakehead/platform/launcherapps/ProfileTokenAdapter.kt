package me.foxtails.lakehead.platform.launcherapps

import android.os.UserHandle
import android.os.UserManager
import android.content.pm.LauncherApps
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ProfileDisplay
import me.foxtails.lakehead.core.model.ProfileDisplayKind

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

    fun profileDisplayFor(identity: AppIdentity, sortKey: String, user: UserHandle): ProfileDisplay {
        val kind = if (isManagedProfile(user)) {
            ProfileDisplayKind.WORK
        } else {
            // Keep the mapping boundary explicit so a Shelter profile can be added here later.
            ProfileDisplayKind.PERSONAL
        }
        val displayLabel = when (kind) {
            ProfileDisplayKind.PERSONAL -> sortKey
            ProfileDisplayKind.WORK -> "Work $sortKey"
            ProfileDisplayKind.SHELTER -> "Shelter $sortKey"
        }
        return ProfileDisplay(identity, sortKey, displayLabel, kind)
    }

    private fun isManagedProfile(user: UserHandle): Boolean {
        try {
            val launcherUserInfo = LauncherApps::class.java
                .getMethod("getLauncherUserInfo", UserHandle::class.java)
                .invoke(launcherApps, user)
            val userType = launcherUserInfo?.javaClass?.getMethod("getUserType")
                ?.invoke(launcherUserInfo) as? String
            if (userType != null) return userType.endsWith("MANAGED")
        } catch (_: ReflectiveOperationException) {
            // Fall through to the older UserInfo classification below.
        }
        // UserInfo exposes this classification on platform releases that support profiles.
        // Keep the reflection at this Android boundary; the domain model stays framework-free.
        return try {
            val userInfo = UserManager::class.java
                .getMethod("getUserInfo", Int::class.javaPrimitiveType)
                .invoke(userManager, UserHandle::class.java.getMethod("getIdentifier").invoke(user) as Int)
            userInfo?.javaClass?.getMethod("isManagedProfile")?.invoke(userInfo) as? Boolean
                ?: false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }
}
