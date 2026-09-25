package me.foxtails.lakehead.core.model

/** Opaque stable identity for one placed item, independent of the app it references. */
@JvmInline
value class ItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "ItemId must not be blank" }
    }
}
