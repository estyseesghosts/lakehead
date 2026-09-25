package me.foxtails.lakehead.core.model

/**
 * Opaque platform-assigned profile token that remains stable for the lifetime of a profile and
 * across process restarts. `:platform:launcherapps` must resolve it to the corresponding
 * accessible profile at the platform boundary. Core must not interpret its format or expose
 * `UserHandle`.
 */
@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProfileId must not be blank" }
    }
}
