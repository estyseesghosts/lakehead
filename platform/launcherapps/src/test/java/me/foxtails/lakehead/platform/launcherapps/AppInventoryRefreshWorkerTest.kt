package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppInventoryRefreshWorkerTest {
    private val item = entry("sample.app", "Main", "Sample")

    @Test fun startPublishesInitialRefresh() = runTest {
        val inventory = AppInventory()
        val worker = AppInventoryRefreshWorker(inventory, { listOf(item) }, StandardTestDispatcher(testScheduler))

        worker.start(backgroundScope)
        runCurrent()

        assertEquals(listOf(item), inventory.apps.value)
        val published = inventory.apps.value.single()
        assertEquals(item.identity, published.identity)
        assertTrue("Published labels must be non-blank", published.label.isNotBlank())
    }

    @Test fun invalidationPublishesOneSubsequentUpdate() = runTest {
        val inventory = AppInventory()
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, { calls++; if (calls == 1) emptyList() else listOf(item) }, StandardTestDispatcher(testScheduler))
        worker.start(backgroundScope)
        runCurrent()

        worker.invalidate()
        runCurrent()

        assertEquals(2, calls)
        assertEquals(listOf(item), inventory.apps.value)
    }

    @Test fun rapidInvalidationsCoalesceAndRefreshLatestResult() = runTest {
        val inventory = AppInventory()
        val firstRefresh = CompletableDeferred<Unit>()
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, {
            calls++
            if (calls == 1) {
                firstRefresh.await()
                emptyList()
            } else listOf(item)
        }, StandardTestDispatcher(testScheduler))
        worker.start(backgroundScope)
        runCurrent()
        worker.invalidate()
        worker.invalidate()
        worker.invalidate()
        firstRefresh.complete(Unit)
        runCurrent()

        assertEquals(2, calls)
        assertEquals(listOf(item), inventory.apps.value)
    }

    @Test fun failedRefreshRetainsLastPublishedSnapshot() = runTest {
        val inventory = AppInventory()
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, {
            calls++
            if (calls == 1) listOf(item) else error("enumeration failed")
        }, StandardTestDispatcher(testScheduler))
        worker.start(backgroundScope)
        runCurrent()
        worker.invalidate()
        runCurrent()

        assertEquals(listOf(item), inventory.apps.value)
    }

    @Test fun stopPreventsQueuedRefreshPublication() = runTest {
        val inventory = AppInventory()
        val refresh = CompletableDeferred<List<LauncherAppEntry>>()
        val worker = AppInventoryRefreshWorker(inventory, { refresh.await() }, StandardTestDispatcher(testScheduler))
        worker.start(backgroundScope)
        runCurrent()
        worker.invalidate()
        worker.stop()
        refresh.complete(listOf(item))
        runCurrent()

        assertEquals(emptyList<LauncherAppEntry>(), inventory.apps.value)
    }

    @Test fun restartWaitsForCancellationInsensitiveRefreshAndDoesNotPublishStaleResult() = runTest {
        val inventory = AppInventory()
        val oldRefresh = CompletableDeferred<Unit>()
        val oldStarted = CompletableDeferred<Unit>()
        val newRefresh = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val staleItem = entry("stale.app", "Main", "Stale")
        val newItem = entry("new.app", "Main", "New")
        var active = 0
        var maxActive = 0
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, {
            calls++
            active++
            maxActive = maxOf(maxActive, active)
            try {
                if (calls == 1) {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { oldRefresh.await() }
                    listOf(staleItem)
                } else {
                    newStarted.complete(Unit)
                    newRefresh.await()
                    listOf(newItem)
                }
            } finally {
                active--
            }
        }, StandardTestDispatcher(testScheduler))

        worker.start(backgroundScope)
        runCurrent()
        oldStarted.await()
        worker.stop()
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, calls)
        oldRefresh.complete(Unit)
        runCurrent()
        newStarted.await()

        assertEquals(2, calls)
        assertEquals(1, maxActive)
        assertEquals(emptyList<LauncherAppEntry>(), inventory.apps.value)

        newRefresh.complete(Unit)
        runCurrent()

        assertEquals(listOf(newItem), inventory.apps.value)
    }

    @Test fun queuedInvalidationDoesNotCarryAcrossRestart() = runTest {
        val inventory = AppInventory()
        val firstRefresh = CompletableDeferred<Unit>()
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, {
            calls++
            if (calls == 1) firstRefresh.await()
            listOf(item)
        }, StandardTestDispatcher(testScheduler))

        worker.start(backgroundScope)
        runCurrent()
        worker.invalidate()
        worker.stop()
        firstRefresh.complete(Unit)
        runCurrent()
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(2, calls)
    }

    @Test fun startRecoversAfterExternalScopeJobCompletes() = runTest {
        val inventory = AppInventory()
        var calls = 0
        val worker = AppInventoryRefreshWorker(inventory, { calls++; listOf(item) }, StandardTestDispatcher(testScheduler))
        val firstScopeJob = Job()
        worker.start(CoroutineScope(firstScopeJob + StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertEquals(1, calls)

        firstScopeJob.cancel()
        runCurrent()
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(2, calls)
        assertEquals(listOf(item), inventory.apps.value)
    }

    private fun entry(packageName: String, className: String, label: String) = LauncherAppEntry(
        AppIdentity(ProfileId("personal"), ComponentIdentity(packageName, className)),
        label,
    )
}
