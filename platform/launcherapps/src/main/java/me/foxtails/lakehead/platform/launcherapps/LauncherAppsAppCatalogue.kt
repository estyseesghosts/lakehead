package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.DisposableHandle
import me.foxtails.lakehead.core.model.LauncherAppEntry

/** Public lifecycle facade for the profile-aware launcher activity inventory. */
class LauncherAppsAppCatalogue(
    context: Context,
    private val callerScope: CoroutineScope,
) : AppCatalogue {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val tokens = ProfileTokenAdapter(launcherApps, userManager)
    private val enumerator = LauncherActivityIdentityEnumerator(launcherApps, tokens)
    private val inventory = AppInventory()
    private val worker = AppInventoryRefreshWorker(inventory, ::fullRefresh)
    private val lifecycleLock = Any()
    private var callback: LauncherAppsInvalidationCallback? = null
    private var receiver: ProfileLifecycleInvalidationReceiver? = null
    private var scopeCompletionHandle: DisposableHandle? = null

    override val apps: StateFlow<List<LauncherAppEntry>> = inventory.apps

    /** Registers invalidation sources and begins the initial asynchronous inventory refresh. */
    fun start() {
        synchronized(lifecycleLock) {
            val scopeJob = callerScope.coroutineContext[Job]
                ?: throw IllegalStateException("callerScope must contain a Job")
            check(scopeJob.isActive) { "Cannot start catalogue with a completed callerScope Job" }
            if (callback != null) return
            val newCallback = LauncherAppsInvalidationCallback(worker::invalidate)
            val newReceiver = ProfileLifecycleInvalidationReceiver(worker::invalidate)
            var callbackRegistered = false
            var receiverRegistered = false
            try {
                launcherApps.registerCallback(newCallback)
                callbackRegistered = true
                callback = newCallback
                val filter = IntentFilter().apply {
                    ProfileLifecycleInvalidationReceiver.supportedActions.forEach(::addAction)
                }
                // These are protected platform profile broadcasts: only the system may send them.
                // EXPORTED is needed because the system sender is outside this application UID.
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(newReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(newReceiver, filter)
                }
                receiverRegistered = true
                receiver = newReceiver
                worker.start(callerScope)
                scopeCompletionHandle = scopeJob.invokeOnCompletion { stop() }
                check(scopeJob.isActive) { "Cannot start catalogue with a completed callerScope Job" }
            } catch (failure: Throwable) {
                worker.stop()
                val shouldUnregisterReceiver = receiverRegistered && receiver === newReceiver
                val shouldUnregisterCallback = callbackRegistered && callback === newCallback
                receiver = null
                callback = null
                scopeCompletionHandle?.dispose()
                scopeCompletionHandle = null
                if (shouldUnregisterReceiver) {
                    try {
                        appContext.unregisterReceiver(newReceiver)
                    } catch (cleanupFailure: Exception) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                if (shouldUnregisterCallback) {
                    try {
                        launcherApps.unregisterCallback(newCallback)
                    } catch (cleanupFailure: Exception) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }
        }
    }

    /** Stops refresh work and unregisters both invalidation sources; safe to call repeatedly. */
    fun stop() {
        synchronized(lifecycleLock) {
            worker.stop()
            val registeredReceiver = receiver
            val registeredCallback = callback
            val completionHandle = scopeCompletionHandle
            receiver = null
            callback = null
            scopeCompletionHandle = null
            completionHandle?.dispose()
            var failure: RuntimeException? = null
            try {
                registeredReceiver?.let(appContext::unregisterReceiver)
            } catch (exception: RuntimeException) {
                failure = exception
            }
            try {
                registeredCallback?.let(launcherApps::unregisterCallback)
            } catch (exception: RuntimeException) {
                if (failure == null) failure = exception else failure.addSuppressed(exception)
            }
            failure?.let { throw it }
        }
    }

    private fun fullRefresh(): List<LauncherAppEntry> {
        val accessibleProfiles = launcherApps.profiles
        val profileIds = mapAccessibleProfiles(accessibleProfiles, tokens::profileIdFor)
        return profileIds.flatMap(enumerator::enumerate)
    }
}
