package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import java.util.Collections

/**
 * Owns the immutable, profile-aware snapshot published by [AppCatalogue]. Snapshots are ordered
 * deterministically by profile, package and class. Entries are de-duplicated by
 * [me.foxtails.lakehead.core.model.AppIdentity], so one component can never appear twice with
 * competing labels; when duplicates disagree on a label, the lexicographically smallest label wins
 * so the published snapshot cannot depend on input order.
 */
internal class AppInventory : AppCatalogue {
    private val mutableApps = MutableStateFlow<List<LauncherAppEntry>>(emptyList())
    private val lock = Any()

    override val apps: StateFlow<List<LauncherAppEntry>> = mutableApps.asStateFlow()

    fun replaceAll(entries: List<LauncherAppEntry>) {
        synchronized(lock) {
            mutableApps.value = snapshot(entries)
        }
    }

    fun replaceProfile(profileId: ProfileId, entries: List<LauncherAppEntry>) {
        synchronized(lock) {
            mutableApps.value = snapshot(
                mutableApps.value.filterNot { it.identity.profileId == profileId } +
                    entries.filter { it.identity.profileId == profileId },
            )
        }
    }

    fun removeProfile(profileId: ProfileId) {
        synchronized(lock) {
            mutableApps.value = snapshot(mutableApps.value.filterNot { it.identity.profileId == profileId })
        }
    }

    private fun snapshot(entries: List<LauncherAppEntry>): List<LauncherAppEntry> =
        Collections.unmodifiableList(
            entries.sortedWith(
                compareBy<LauncherAppEntry> { it.identity.profileId.value }
                    .thenBy { it.identity.component.packageName }
                    .thenBy { it.identity.component.className }
                    .thenBy { it.label },
            ).distinctBy { it.identity },
        )
}
