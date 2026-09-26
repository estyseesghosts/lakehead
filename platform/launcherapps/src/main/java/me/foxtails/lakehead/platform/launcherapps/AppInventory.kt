package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import java.text.Collator
import java.util.Collections
import java.util.Locale

/**
 * Owns the immutable, profile-aware snapshot published by [AppCatalogue]. Snapshots are ordered
 * by display label (case-insensitive, locale-aware via [Collator]) as the primary key so the
 * catalogue reads in natural A→Z order. Package name, class name and profile ID serve as
 * deterministic tie-breakers; profile is last so the flat list is label-first across all
 * profiles, with profile grouping only when labels, package and class coincide.
 *
 * Entries are de-duplicated by [me.foxtails.lakehead.core.model.AppIdentity], so one component
 * can never appear twice with competing labels; when duplicates disagree on a label, the
 * lexicographically smallest label wins so the published snapshot cannot depend on input order.
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

    private fun snapshot(entries: List<LauncherAppEntry>): List<LauncherAppEntry> {
        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        return Collections.unmodifiableList(
            entries.sortedWith(
                compareBy<LauncherAppEntry, String>(collator) { it.label }
                    .thenBy { it.identity.component.packageName }
                    .thenBy { it.identity.component.className }
                    .thenBy { it.identity.profileId.value },
            ).distinctBy { it.identity },
        )
    }
}
