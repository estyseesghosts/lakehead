package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.MediaAlbum

/**
 * Floating album picker over Home listing device albums (name and count).
 *
 * Takes a plain album list and reports the chosen [AlbumRef]; it owns no
 * `MediaStore` query, permission request, thumbnail loading or persistence
 * (slice 6 wires the coordinator). An empty list renders the honest empty row
 * instead of no overlay. Back and the scrim dismiss.
 */
@Composable
fun AlbumPickerOverlay(
    isOpen: Boolean,
    albums: List<MediaAlbum>,
    onPick: (AlbumRef) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().testTag(ALBUM_PICKER_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(ALBUM_PICKER_SCRIM_TAG)
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 64.dp)
                .heightIn(max = 520.dp)
                .background(scheme.surfaceContainer)
                .padding(16.dp),
        ) {
            BasicText(
                text = "Choose album",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
            )
            if (albums.isEmpty()) {
                BasicText(
                    text = "No albums found",
                    style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 14.sp),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag(ALBUM_PICKER_EMPTY_TAG),
                )
            }
            LazyColumn {
                items(albums, key = { it.album.bucketId }) { album ->
                    BasicText(
                        text = "${album.album.name} (${album.photoCount})",
                        style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(ALBUM_PICKER_ROW_TAG_PREFIX + album.album.bucketId)
                            .clickable(role = Role.Button) { onPick(album.album) },
                    )
                }
            }
        }
    }
}

/** Test tag on the album picker overlay. */
const val ALBUM_PICKER_TAG: String = "album-picker"

/** Test tag on the album picker scrim (cancel path). */
const val ALBUM_PICKER_SCRIM_TAG: String = "album-picker-scrim"

/** Test tag on the album picker empty row. */
const val ALBUM_PICKER_EMPTY_TAG: String = "album-picker-empty"

/** Test-tag prefix for album rows; the bucket id is appended. */
const val ALBUM_PICKER_ROW_TAG_PREFIX: String = "album-row-"
