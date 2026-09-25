package me.foxtails.lakehead.core.model

/** Identifies an application component without any platform framework types. */
data class ComponentIdentity(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(className.isNotBlank()) { "className must not be blank" }
    }
}
