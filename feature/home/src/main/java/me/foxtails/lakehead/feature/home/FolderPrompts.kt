package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import me.foxtails.lakehead.core.designsystem.FolderTile
import me.foxtails.lakehead.core.model.ItemId

/**
 * Floating chooser listing Home's folders by their previews; a pick reports the folder id.
 * The scrim and Back dismiss. Presentation only: the caller performs the move.
 */
@Composable
fun FolderChooserOverlay(
    isOpen: Boolean,
    folders: List<HomeRenderTile>,
    iconsByIdentity: HomeCanvasIcons,
    onPick: (ItemId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().testTag(FOLDER_CHOOSER_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(FOLDER_CHOOSER_SCRIM_TAG)
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 480.dp)
                .background(scheme.surfaceContainer)
                .padding(16.dp),
        ) {
            BasicText(
                text = "Add to folder",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = { it.itemId.value }) { folder ->
                    Box(
                        Modifier
                            .size(72.dp)
                            .testTag(FOLDER_CHOOSER_ITEM_TAG_PREFIX + folder.itemId.value),
                    ) {
                        FolderTile(
                            label = folder.label ?: FOLDER_LABEL,
                            previews = folder.folderPreview.orEmpty().map { iconsByIdentity[it] },
                            onClick = { onPick(folder.itemId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Confirmation before removing a non-empty folder together with everything in it.
 * [onConfirm] removes; the scrim, Back and "Cancel" report [onDismiss].
 */
@Composable
fun FolderRemovePrompt(
    isOpen: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().testTag(FOLDER_REMOVE_PROMPT_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(FOLDER_REMOVE_SCRIM_TAG)
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
                text = "Remove folder?",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
            )
            BasicText(
                text = "The folder and everything in it will be removed from Home.",
                style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 14.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                BasicText(
                    text = "Cancel",
                    style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(FOLDER_REMOVE_CANCEL_TAG)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                )
                BasicText(
                    text = "Remove",
                    style = TextStyle(color = scheme.error, fontSize = 16.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(FOLDER_REMOVE_CONFIRM_TAG)
                        .clickable(role = Role.Button, onClick = onConfirm)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

const val FOLDER_CHOOSER_TAG: String = "folder-chooser"
const val FOLDER_CHOOSER_SCRIM_TAG: String = "folder-chooser-scrim"
const val FOLDER_CHOOSER_ITEM_TAG_PREFIX: String = "folder-chooser-item-"
const val FOLDER_REMOVE_PROMPT_TAG: String = "folder-remove-prompt"
const val FOLDER_REMOVE_SCRIM_TAG: String = "folder-remove-scrim"
const val FOLDER_REMOVE_CANCEL_TAG: String = "folder-remove-cancel"
const val FOLDER_REMOVE_CONFIRM_TAG: String = "folder-remove-confirm"
