package me.foxtails.lakehead.feature.allapps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * Flat All Apps list screen.
 *
 * Presentation only: [state] and [icons] are caller-acquired. Icons are already-decoded
 * snapshots resolved with [Map.get] on the exact [AppIdentity]; this composable performs no
 * decode, catalogue/platform lookup, or database access, preserves the caller's catalogue
 * order, and uses Foundation text/layout only (no Material product components).
 *
 * - [AllAppsViewState.Loading] renders honest loading text.
 * - [AllAppsViewState.Loaded] with no entries renders an honest empty message.
 * - [AllAppsViewState.Loaded] with entries renders a [LazyColumn] of [AllAppsRow] rows in
 *   input order; row taps launch via [onLaunchApp] and Add taps request placement via
 *   [onAddToHome], each with the row's exact [AppIdentity].
 */
@Composable
fun AllAppsScreen(
    state: AllAppsViewState,
    icons: Map<AppIdentity, ImageBitmap>,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
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
                LazyColumn(
                    modifier = modifier.testTag(ALL_APPS_LIST_TAG),
                ) {
                    items(
                        items = state.entries,
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
