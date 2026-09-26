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
                val sections = remember(state.entries) { state.entries.toAppSections() }
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
