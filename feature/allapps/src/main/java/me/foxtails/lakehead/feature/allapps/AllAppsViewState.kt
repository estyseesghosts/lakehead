package me.foxtails.lakehead.feature.allapps

import me.foxtails.lakehead.core.model.AppIdentity

/** Immutable row state for one app in the All Apps list. */
data class AllAppsEntry(
    val identity: AppIdentity,
    val label: String,
    val sortKey: String = label,
    val displayLabel: String = label,
    val alreadyOnHome: Boolean,
)

/** View state for the All Apps screen. */
sealed interface AllAppsViewState {
    data object Loading : AllAppsViewState
    data class Loaded(val entries: List<AllAppsEntry>) : AllAppsViewState
}
