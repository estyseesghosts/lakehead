package me.foxtails.lakehead.core.model

/** A component identity scoped to the profile in which it is available. */
data class AppIdentity(
    val profileId: ProfileId,
    val component: ComponentIdentity,
)
