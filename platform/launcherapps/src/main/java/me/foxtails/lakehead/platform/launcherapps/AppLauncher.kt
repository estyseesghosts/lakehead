package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity

/** Launches an app identified by its profile-aware, framework-free domain identity. */
fun interface AppLauncher {
    /** Returns false when this identity cannot currently be launched. */
    fun launch(identity: AppIdentity): Boolean
}
