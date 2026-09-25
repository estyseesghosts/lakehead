package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.ProfileId

/** Maps the complete accessible profile set or fails rather than publishing a partial inventory. */
internal fun <T> mapAccessibleProfiles(
    profiles: List<T>,
    mapProfile: (T) -> ProfileId?,
): List<ProfileId> = profiles.map { profile ->
    requireNotNull(mapProfile(profile)) { "An accessible profile could not be mapped" }
}
