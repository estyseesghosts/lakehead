package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity

/**
 * Immutable platform-boundary icon data: PNG-encoded bytes and the complete profile-aware app
 * identity they belong to. Input and output byte arrays are copied. Decoding is deliberately left
 * to a platform/rendering boundary; this value is not a core model or persistence type.
 */
class AppIconSnapshot(identity: AppIdentity, pngBytes: ByteArray) {
    val identity: AppIdentity = identity

    private val imageBytes = pngBytes.copyOf().also {
        require(it.isNotEmpty()) { "PNG bytes must not be empty" }
        require(it.size <= MAX_PNG_BYTES) { "PNG bytes exceed $MAX_PNG_BYTES bytes" }
    }

    /** Returns a defensive copy of the encoded PNG. */
    val pngBytes: ByteArray
        get() = imageBytes.copyOf()

    companion object {
        /** Caps individual snapshots so the entry-bounded cache also has a finite byte bound. */
        const val MAX_PNG_BYTES: Int = 1_048_576
    }
}
