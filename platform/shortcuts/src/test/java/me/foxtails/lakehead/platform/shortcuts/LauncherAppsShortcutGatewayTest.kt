package me.foxtails.lakehead.platform.shortcuts

import android.content.pm.LauncherApps
import android.os.UserHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gateway fence/stop regressions.
 *
 * The fake platform blocks inside [NonCancellable] to simulate the
 * production blocking `LauncherApps.getShortcuts` call, which ignores
 * coroutine cancellation: suppression must come from the version fence
 * (removal/stop invalidation), not merely from job cancellation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherAppsShortcutGatewayTest {
    private val profile = ProfileId("10")
    private fun identity(profileId: ProfileId = profile, pkg: String = "com.gone") =
        AppIdentity(profileId, ComponentIdentity(pkg, "Main"))

    @Test
    fun removalDuringInFlightQueryKeepsRemovedAndNeverRestoresStale() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        // Populate the cache first so removal has a visible entry to drop.
        fake.rowsToReturn = listOf(ShortcutQueryRow("old", "Old", null, 0))
        fake.gate = null
        gateway.refresh(id)
        advanceUntilIdle()
        assertEquals(listOf("old"), gateway.shortcutsFor(id).map { it.ref.shortcutId })
        assertTrue(gateway.shortcuts.value.containsKey(id))

        // Start an in-flight refresh that blocks, then remove while blocked.
        fake.rowsToReturn = listOf(ShortcutQueryRow("stale", "Stale", null, 0))
        fake.resetGate()
        gateway.refresh(id)
        runCurrent()
        assertTrue(fake.entered.isCompleted)

        gateway.dropPackage("com.gone", profile)
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertFalse(gateway.shortcuts.value.containsKey(id))

        // The stale query completes afterwards: it must not restore.
        fake.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertFalse(gateway.shortcuts.value.containsKey(id))
        gateway.stop()
    }

    @Test
    fun removalDuringColdInFlightQueryNeverPublishesStale() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        // No cached entry: the in-flight query is cold. Removal still fences
        // the tracked version so the stale result never publishes.
        fake.rowsToReturn = listOf(ShortcutQueryRow("stale", "Stale", null, 0))
        fake.resetGate()
        gateway.refresh(id)
        runCurrent()
        assertTrue(fake.entered.isCompleted)

        gateway.dropPackage("com.gone", profile)
        fake.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertFalse(gateway.shortcuts.value.containsKey(id))
        gateway.stop()
    }

    @Test
    fun pendingRefreshStoppedBeforeCompletionNeverPublishes() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        fake.rowsToReturn = listOf(ShortcutQueryRow("late", "Late", null, 0))
        fake.resetGate()
        gateway.refresh(id)
        runCurrent()
        assertTrue(fake.entered.isCompleted)

        gateway.stop()
        fake.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertTrue(gateway.shortcuts.value.isEmpty())
        // Post-stop refresh requests are dropped, never queried.
        val calls = fake.calls.size
        gateway.refresh(id)
        advanceUntilIdle()
        assertEquals(calls, fake.calls.size)
        assertFalse(gateway.refreshAndAwait(id))
    }

    @Test
    fun refreshAndAwaitStoppedDuringQueryNeverPublishesAndReturnsFalse() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        // Admit an awaitable query owned by workJob, then stop while blocked.
        fake.rowsToReturn = listOf(ShortcutQueryRow("late", "Late", null, 0))
        fake.resetGate()
        val awaitable = async { gateway.refreshAndAwait(id) }
        runCurrent()
        assertTrue(fake.entered.isCompleted)

        gateway.stop()
        fake.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(awaitable.await())
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertTrue(gateway.shortcuts.value.isEmpty())
        gateway.stop()
    }

    @Test
    fun refreshAndAwaitAfterStopAdmitsNothing() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)
        gateway.stop()

        // Post-stop admission is atomic: no stamp, no platform query.
        fake.rowsToReturn = listOf(ShortcutQueryRow("late", "Late", null, 0))
        fake.gate = null
        val calls = fake.calls.size
        assertFalse(gateway.refreshAndAwait(id))
        advanceUntilIdle()
        assertEquals(calls, fake.calls.size)
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        gateway.stop()
    }

    @Test
    fun refreshAndAwaitSuccessPublishesAndReturnsTrue() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        fake.rowsToReturn = listOf(ShortcutQueryRow("ok", "Ok", null, 2))
        fake.gate = null
        assertTrue(gateway.refreshAndAwait(id))
        assertEquals(listOf("ok"), gateway.shortcutsFor(id).map { it.ref.shortcutId })
        gateway.stop()
    }

    @Test
    fun stopUnregistersCallbackAndIsRepeatable() = runTest {        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)
        assertEquals(1, fake.registered.size)
        gateway.stop()
        assertEquals(1, fake.unregistered.size)
        gateway.stop()
        assertEquals(1, fake.unregistered.size)
    }

    @Test
    fun callerCancelCancelsAdmittedQueryAndNeverPublishes() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        // Admit an awaitable query that blocks in the platform, then cancel
        // the caller while blocked. The admitted query must be cancelled with
        // the caller so releasing the platform afterwards never publishes.
        fake.rowsToReturn = listOf(ShortcutQueryRow("late", "Late", null, 0))
        fake.resetGate()
        val awaiting = async { gateway.refreshAndAwait(id) }
        runCurrent()
        assertTrue(fake.entered.isCompleted)

        awaiting.cancel()
        runCurrent()
        assertTrue(awaiting.isCancelled)

        fake.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(gateway.shortcutsFor(id).isEmpty())
        assertTrue(gateway.shortcuts.value.isEmpty())
        assertEquals(0, gateway.fenceOutstandingForTest(id))

        // The gateway stays usable after a caller cancel.
        fake.gate = null
        fake.rowsToReturn = listOf(ShortcutQueryRow("ok", "Ok", null, 0))
        assertTrue(gateway.refreshAndAwait(id))
        assertEquals(listOf("ok"), gateway.shortcutsFor(id).map { it.ref.shortcutId })
        gateway.stop()
    }

    @Test
    fun callbackRefreshFailuresRetireFenceAndKeepLastGood() = runTest {
        val id = identity()
        val fake = FakeShortcutPlatform()
        val gateway = LauncherAppsShortcutGateway(fake, StandardTestDispatcher(testScheduler))
        gateway.start(backgroundScope)

        // Populate last-good first so failures have a snapshot to keep.
        fake.rowsToReturn = listOf(ShortcutQueryRow("old", "Old", null, 0))
        fake.gate = null
        gateway.refresh(id)
        advanceUntilIdle()
        assertEquals(listOf("old"), gateway.shortcutsFor(id).map { it.ref.shortcutId })

        // Repeated callback-refresh failures must not accumulate fence state:
        // each stamp is abandoned and retired, the snapshot keeps last-good.
        fake.errorToThrow = RuntimeException("boom")
        repeat(5) {
            gateway.refreshPackage("com.gone", null)
            advanceUntilIdle()
        }
        assertEquals(listOf("old"), gateway.shortcutsFor(id).map { it.ref.shortcutId })
        assertEquals(0, gateway.fenceOutstandingForTest(id))
        assertEquals(1, gateway.fenceSizeForTest())

        // Recovery still publishes after the failure streak.
        fake.errorToThrow = null
        fake.rowsToReturn = listOf(ShortcutQueryRow("new", "New", null, 0))
        gateway.refreshPackage("com.gone", null)
        advanceUntilIdle()
        assertEquals(listOf("new"), gateway.shortcutsFor(id).map { it.ref.shortcutId })
        assertEquals(0, gateway.fenceOutstandingForTest(id))
        gateway.stop()
    }

    private class FakeShortcutPlatform : ShortcutPlatform {
        val calls = mutableListOf<AppIdentity>()
        var rowsToReturn: List<ShortcutQueryRow> = emptyList()
        var errorToThrow: RuntimeException? = null
        var gate: CompletableDeferred<Unit>? = null
        var entered = CompletableDeferred<Unit>()
        val registered = mutableListOf<LauncherApps.Callback>()
        val unregistered = mutableListOf<LauncherApps.Callback>()

        fun resetGate() {
            gate = CompletableDeferred()
            entered = CompletableDeferred()
        }

        override suspend fun queryRows(identity: AppIdentity): List<ShortcutQueryRow>? {
            calls += identity
            entered.complete(Unit)
            errorToThrow?.let { throw it }
            // NonCancellable simulates the blocking platform call: gateway
            // stop cancels the query job, but this section still runs to
            // completion, so only the version fence can suppress the commit.
            gate?.let { withContext(NonCancellable) { it.await() } }
            return rowsToReturn.toList()
        }

        override fun launch(ref: ShortcutRef): Boolean = true

        override fun registerCallback(callback: LauncherApps.Callback) {
            registered += callback
        }

        override fun unregisterCallback(callback: LauncherApps.Callback) {
            unregistered += callback
        }

        override fun profileIdFor(user: UserHandle): ProfileId? = null
    }
}
