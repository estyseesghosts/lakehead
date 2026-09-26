package me.foxtails.lakehead.feature.allapps

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * Grouped All Apps list screen.
 *
 * Presentation only: [state] and [icons] are caller-acquired. Icons are already-decoded
 * snapshots resolved with [Map.get] on the exact [AppIdentity]; this composable performs no
 * decode, catalogue/platform lookup, or database access, and uses Foundation text/layout only
 * (no Material product components).
 *
 * - [AllAppsViewState.Loading] renders honest loading text.
 * - [AllAppsViewState.Loaded] with no entries renders an honest empty message (no headers).
 * - [AllAppsViewState.Loaded] with entries groups [AllAppsEntry] items into [AppSection]s
 *   via [toAppSections] (remembered on the entries) and renders a [LazyColumn] where each
 *   section is a full-row [AllAppsSectionHeader] `item(key = "section:{key}")` followed by
 *   that section's [AllAppsRow] rows (`items(apps, key = rowKey)`). A single section is
 *   still headered. Headers scroll with content (plain `item`, never sticky).
 * - Row taps launch via [onLaunchApp] and Add taps request placement via [onAddToHome],
 *   each with the row's exact [AppIdentity]. Header taps dispatch the section key via
 *   [onHeaderTap].
 * - Optional P4 picker: when [picker] is non-null the list renders inside
 *   [AlphabetPickerOverlay] (scrim dismiss via [onPickerDismiss], letter jumps via
 *   [onPickerLetterPicked]). Null renders the bare list, preserving existing callers.
 *   The [LazyListState] ([listState]) is hoisted above the overlay so dismiss preserves
 *   scroll position; callers block horizontal paging while `picker?.isOpen == true`
 *   (see [blocksPager]).
 */
@Composable
fun AllAppsScreen(
    state: AllAppsViewState,
    icons: Map<AppIdentity, ImageBitmap>,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
    onHeaderTap: (String) -> Unit = {},
    picker: AlphabetPickerState? = null,
    onPickerDismiss: () -> Unit = {},
    onPickerLetterPicked: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    when (state) {
        is AllAppsViewState.Loading -> BasicText(
            text = LOADING_TEXT,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )

        is AllAppsViewState.Loaded -> {
            if (state.entries.isEmpty()) {
                BasicText(
                    text = EMPTY_TEXT,
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                val anchorState = remember { AnchorRestoreState() }

                // Capture anchor before sections recomputation: uses the PREVIOUS
                // sections (still in anchorState) and the current listState (which
                // still reflects the old LazyColumn layout).
                if (anchorState.lastEntries != null &&
                    anchorState.lastEntries != state.entries &&
                    anchorState.lastSections.isNotEmpty()
                ) {
                    anchorState.capturedAnchor = deriveAnchor(listState, anchorState.lastSections)
                }

                val sections = remember(state.entries) { state.entries.toAppSections() }

                // Snapshot for next recomposition.
                anchorState.lastEntries = state.entries
                anchorState.lastSections = sections

                // Restore scroll position after section regen: prefer the exact
                // entry if it still exists, else fall back to the section header.
                // Empty sections → null anchor → no-op.
                LaunchedEffect(sections) {
                    val anchor = anchorState.capturedAnchor
                    if (anchor != null && sections.isNotEmpty()) {
                        val entryIndex = if (anchor.entryKey != null) {
                            findEntryLazyIndex(sections, anchor.entryKey)
                        } else null

                        if (entryIndex != null) {
                            listState.animateScrollToItem(entryIndex)
                        } else {
                            val headerIndex = findSectionHeaderLazyIndex(sections, anchor.sectionKey)
                            if (headerIndex != null) {
                                listState.animateScrollToItem(headerIndex)
                            }
                        }
                    }
                    anchorState.capturedAnchor = null
                }

                if (picker != null) {
                    AlphabetPickerOverlay(
                        picker = picker,
                        onDismiss = onPickerDismiss,
                        onLetterPicked = onPickerLetterPicked,
                        modifier = modifier,
                    ) {
                        AllAppsList(
                            sections = sections,
                            icons = icons,
                            onLaunchApp = onLaunchApp,
                            onAddToHome = onAddToHome,
                            bottomClearance = bottomClearance,
                            onHeaderTap = onHeaderTap,
                            listState = listState,
                            modifier = Modifier,
                        )
                    }
                } else {
                    AllAppsList(
                        sections = sections,
                        icons = icons,
                        onLaunchApp = onLaunchApp,
                        onAddToHome = onAddToHome,
                        bottomClearance = bottomClearance,
                        onHeaderTap = onHeaderTap,
                        listState = listState,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

/** Honest loading text for [AllAppsViewState.Loading]. */
const val LOADING_TEXT: String = "Loading apps…"

/** Honest empty text for [AllAppsViewState.Loaded] with no entries. */
const val EMPTY_TEXT: String = "No apps found"

/** Test tag on the All Apps list (testing hook only; no behavior change). */
const val ALL_APPS_LIST_TAG: String = "allapps-list"

/**
 * Stable `LazyColumn` key for one [AllAppsEntry].
 *
 * `LazyColumn` keys must be saveable (Bundle-compatible) types, so the [AppIdentity] itself
 * cannot be used. The profile, package, and class parts are joined with a unit separator
 * that cannot appear in Java package/class names, keeping distinct identities distinct.
 */
internal fun AllAppsEntry.rowKey(): String =
    "${identity.profileId.value}\u001F${identity.component.packageName}\u001F${identity.component.className}"

/**
 * Captured scroll-position anchor for restoring the All Apps list after a section regen.
 *
 * [entryKey] is the [rowKey] of the first visible entry, or null when the first visible item
 * was a section header. [sectionKey] is the section key of that entry's parent section,
 * or the header's own section key when [entryKey] is null.
 */
internal data class AllAppsAnchor(
    val entryKey: String?,
    val sectionKey: String,
)

/**
 * Mutable holder for anchor capture/restore across recompositions.
 *
 * Not a Compose State: field writes do not trigger recomposition. The [LaunchedEffect] keyed
 * on sections handles restore; the composable reads [capturedAnchor] after composition.
 */
private class AnchorRestoreState {
    var lastEntries: List<AllAppsEntry>? = null
    var lastSections: List<AppSection> = emptyList()
    var capturedAnchor: AllAppsAnchor? = null
}

/**
 * Derives a scroll anchor from the current visible items in [listState] and [sections].
 *
 * Scans visible items top-to-bottom: the first entry row yields an anchor with its entry key
 * and parent section key; if only section headers are visible the anchor has null entry key;
 * returns null when [sections] is empty or no items are visible.
 */
internal fun deriveAnchor(
    listState: LazyListState,
    sections: List<AppSection>,
): AllAppsAnchor? {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty() || sections.isEmpty()) return null

    val entryToSection = buildMap<String, String> {
        for (section in sections) {
            for (app in section.apps) {
                put(app.rowKey(), section.key)
            }
        }
    }

    var nearestSectionKey: String? = null
    for (item in visibleItems) {
        val key = item.key as? String ?: continue
        if (key.startsWith("section:")) {
            nearestSectionKey = key.removePrefix("section:")
        } else {
            val sectionKey = entryToSection[key] ?: nearestSectionKey
            if (sectionKey != null) {
                return AllAppsAnchor(entryKey = key, sectionKey = sectionKey)
            }
        }
    }

    if (nearestSectionKey != null) {
        return AllAppsAnchor(entryKey = null, sectionKey = nearestSectionKey)
    }

    return null
}

/**
 * Flat-list index of an entry with [entryKey] in [sections] (headers + entries), or null.
 */
internal fun findEntryLazyIndex(sections: List<AppSection>, entryKey: String): Int? {
    var index = 0
    for (section in sections) {
        index++ // section header
        for (entry in section.apps) {
            if (entry.rowKey() == entryKey) return index
            index++
        }
    }
    return null
}

/**
 * Flat-list index of a section header with [sectionKey] in [sections], or null.
 */
internal fun findSectionHeaderLazyIndex(sections: List<AppSection>, sectionKey: String): Int? {
    var index = 0
    for (section in sections) {
        if (section.key == sectionKey) return index
        index += 1 + section.apps.size
    }
    return null
}

/**
 * Grouped list body shared by the bare and picker-wrapped presentations.
 *
 * Headers are plain `item` rows that scroll with content (never sticky).
 */
@Composable
private fun AllAppsList(
    sections: List<AppSection>,
    icons: Map<AppIdentity, ImageBitmap>,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    bottomClearance: Dp,
    onHeaderTap: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(ALL_APPS_LIST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        sections.forEach { section ->
            item(key = "section:${section.key}") {
                AllAppsSectionHeader(
                    key = section.key,
                    onHeaderTap = onHeaderTap,
                )
            }
            items(
                items = section.apps,
                key = { it.rowKey() },
            ) { entry ->
                AllAppsRow(
                    entry = entry,
                    icon = icons[entry.identity],
                    onLaunchApp = onLaunchApp,
                    onAddToHome = onAddToHome,
                )
            }
        }
    }
}
