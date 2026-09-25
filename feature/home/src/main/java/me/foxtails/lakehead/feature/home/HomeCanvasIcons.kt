package me.foxtails.lakehead.feature.home

import androidx.compose.ui.graphics.ImageBitmap
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * Already-decoded immutable UI icon snapshots keyed by full [AppIdentity].
 *
 * Values are Compose [ImageBitmap] snapshots resolved outside composition by the caller
 * (platform/app wiring). The feature performs no lookup, decode, or cache: it only reads
 * this map with [Map.get] to obtain a pre-decoded bitmap for a tile's exact identity.
 * A missing entry means no icon snapshot was supplied and the tile renders label-only.
 */
typealias HomeCanvasIcons = Map<AppIdentity, ImageBitmap>
