package me.foxtails.lakehead.core.model

/** Stable identity for one independently keyed Home canvas. */
@JvmInline
value class HomeId(val value: String) {
    init {
        require(value.isNotBlank()) { "HomeId must not be blank" }
    }
}
