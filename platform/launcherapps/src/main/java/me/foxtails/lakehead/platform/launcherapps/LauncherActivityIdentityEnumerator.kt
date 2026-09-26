package me.foxtails.lakehead.platform.launcherapps

import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import java.text.Collator
import java.util.Locale

/**
 * Enumerates immutable label-bearing app entries for one accessible profile at the Android
 * boundary. Labels are read from [LauncherActivityInfo.label] on the caller's background refresh
 * path and converted to plain [String] values immediately; a blank label falls back to the
 * component's package name. No [LauncherActivityInfo], [CharSequence], drawable or user handle
 * leaves this boundary. Entries are sorted by display label (case-insensitive, locale-aware via
 * [Collator]) as the primary key, with package name and class name as deterministic tie-breakers.
 * An unresolved profile returns an empty list; enumeration security failures propagate to the
 * caller.
 */
internal class LauncherActivityIdentityEnumerator(
    private val launcherApps: LauncherApps,
    private val profileTokens: ProfileTokenAdapter,
) {
    fun enumerate(profileId: ProfileId): List<LauncherAppEntry> {
        val user = profileTokens.userFor(profileId) ?: return emptyList()
        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        return launcherApps.getActivityList(null, user)
            .asSequence()
            .map { activity ->
                val component = activity.componentName
                val identity = AppIdentity(
                    profileId = profileId,
                    component = ComponentIdentity(component.packageName, component.className),
                )
                LauncherAppEntry(identity, labelFor(activity, identity))
            }
            .sortedWith(
                compareBy<LauncherAppEntry, String>(collator) { it.label }
                    .thenBy { it.identity.component.packageName }
                    .thenBy { it.identity.component.className },
            )
            .distinctBy { it.identity }
            .toList()
    }

    /** Flattens the framework label to a trimmed nonblank [String]; the identity is framework-free. */
    private fun labelFor(activity: LauncherActivityInfo, identity: AppIdentity): String =
        activity.label?.toString()?.trim().orEmpty().ifEmpty { identity.component.packageName }
}
