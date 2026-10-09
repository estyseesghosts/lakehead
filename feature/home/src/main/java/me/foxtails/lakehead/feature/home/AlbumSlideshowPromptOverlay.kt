package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Yes/no prompt offering an album slideshow on a gallery tile.
 *
 * Takes only the gallery [appLabel] and reports the choice; it owns no
 * resize detection, permission request, album listing or persistence (slice 6
 * wires the coordinator). "Yes" reports [onAccept], "No" reports [onDecline],
 * and the scrim or Back reports [onDismiss] (cancel: neither accept nor
 * decline, so the next threshold crossing asks again).
 */
@Composable
fun AlbumSlideshowPromptOverlay(
    isOpen: Boolean,
    appLabel: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().testTag(ALBUM_PROMPT_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(ALBUM_PROMPT_SCRIM_TAG)
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(scheme.surfaceContainer)
                .padding(16.dp),
        ) {
            BasicText(
                text = "Show album photos?",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
            )
            BasicText(
                text = "Show photos from an album on $appLabel? " +
                    "You can change or remove it later.",
                style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 14.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                BasicText(
                    text = "No",
                    style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ALBUM_PROMPT_DECLINE_TAG)
                        .clickable(role = Role.Button, onClick = onDecline)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                )
                BasicText(
                    text = "Yes",
                    style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ALBUM_PROMPT_ACCEPT_TAG)
                        .clickable(role = Role.Button, onClick = onAccept)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

/** Test tag on the album prompt overlay. */
const val ALBUM_PROMPT_TAG: String = "album-prompt"

/** Test tag on the album prompt scrim (cancel path). */
const val ALBUM_PROMPT_SCRIM_TAG: String = "album-prompt-scrim"

/** Test tag on the album prompt "Yes" row. */
const val ALBUM_PROMPT_ACCEPT_TAG: String = "album-prompt-accept"

/** Test tag on the album prompt "No" row. */
const val ALBUM_PROMPT_DECLINE_TAG: String = "album-prompt-decline"
