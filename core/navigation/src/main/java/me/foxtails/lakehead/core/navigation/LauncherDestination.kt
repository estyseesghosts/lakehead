package me.foxtails.lakehead.core.navigation

enum class LauncherDestination {
    FEED,
    NOTIFICATIONS,
    HOME,
    ALL_APPS;

    /** True for destinations that are not yet implemented. */
    val isStub: Boolean
        get() = this == FEED || this == NOTIFICATIONS
}