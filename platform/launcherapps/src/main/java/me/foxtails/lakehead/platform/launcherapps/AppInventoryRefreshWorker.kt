package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.model.LauncherAppEntry

/** Serializes full inventory refreshes while collapsing redundant invalidations. */
internal class AppInventoryRefreshWorker(
    private val inventory: AppInventory,
    private val fullRefresh: suspend () -> List<LauncherAppEntry>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lifecycleLock = Any()
    private val refreshMutex = Mutex()
    private var job: Job? = null
    private var requests: Channel<Unit>? = null
    private var generation = 0L

    fun start(scope: CoroutineScope) {
        synchronized(lifecycleLock) {
            if (job?.isActive == true) return
            job?.cancel()
            requests?.cancel()
            val channel = Channel<Unit>(Channel.CONFLATED)
            requests = channel
            val myGeneration = ++generation
            val newJob = scope.launch(dispatcher) {
                refresh(myGeneration)
                for (ignored in channel) refresh(myGeneration)
            }
            job = newJob
            newJob.invokeOnCompletion {
                synchronized(lifecycleLock) {
                    if (job === newJob) {
                        job = null
                        requests = null
                        channel.close()
                    }
                }
            }
        }
    }

    fun invalidate() {
        synchronized(lifecycleLock) { requests?.trySend(Unit) }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            generation++
            requests?.cancel()
            requests = null
            job?.cancel()
            job = null
        }
    }

    private suspend fun refresh(myGeneration: Long) {
        try {
            refreshMutex.withLock {
                val result = withContext(NonCancellable) { fullRefresh() }
                synchronized(lifecycleLock) {
                    if (generation == myGeneration && job?.isActive == true) {
                        inventory.replaceAll(result)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed enumeration leaves the last known-good inventory published.
        }
    }
}
