package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef

/**
 * Framework-free row extracted from one platform shortcut at the Android
 * boundary. Labels stay as [CharSequence] only until [mapShortcutRows]
 * flattens them to trimmed nonblank strings; ranks are the platform order.
 */
internal data class ShortcutQueryRow(
    val id: String,
    val shortLabel: CharSequence?,
    val longLabel: CharSequence?,
    val rank: Int,
)

/**
 * Maps platform shortcut rows to rank-ordered domain shortcuts for one
 * package and profile. Rows with a blank id are dropped; the label prefers
 * the trimmed short label, then the trimmed long label, then the id itself.
 * Results are sorted by rank (lower first) with the shortcut id as the
 * deterministic tie-breaker, and de-duplicated by id keeping the lowest rank.
 * Pure, framework-free and unit-testable.
 */
internal fun mapShortcutRows(
    packageName: String,
    profileId: ProfileId,
    rows: List<ShortcutQueryRow>,
): List<LauncherShortcut> {
    require(packageName.isNotBlank()) { "packageName must not be blank" }
    return rows
        .asSequence()
        .filter { it.id.isNotBlank() }
        .map { row ->
            val label = row.shortLabel?.toString()?.trim().orEmpty()
                .ifEmpty { row.longLabel?.toString()?.trim().orEmpty() }
                .ifEmpty { row.id }
            ShortcutQueryEntry(row.id, label, row.rank.coerceAtLeast(0))
        }
        .sortedWith(compareBy<ShortcutQueryEntry> { it.rank }.thenBy { it.id })
        .distinctBy { it.id }
        .map { entry ->
            LauncherShortcut(
                ref = ShortcutRef(packageName, profileId, entry.id),
                label = entry.label,
                rank = entry.rank,
            )
        }
        .toList()
}

private data class ShortcutQueryEntry(val id: String, val label: String, val rank: Int)
