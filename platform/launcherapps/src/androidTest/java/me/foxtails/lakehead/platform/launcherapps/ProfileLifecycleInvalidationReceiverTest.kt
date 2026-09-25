package me.foxtails.lakehead.platform.launcherapps

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLifecycleInvalidationReceiverTest {
    @Test
    fun supportedActionsInvalidateOnceAndUnrelatedOrNullActionsAreIgnored() {
        val expectedActions = mutableSetOf(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Intent.ACTION_PROFILE_ADDED)
                add(Intent.ACTION_PROFILE_REMOVED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Intent.ACTION_PROFILE_AVAILABLE)
                add(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
        assertEquals(expectedActions, ProfileLifecycleInvalidationReceiver.supportedActions)

        var invalidations = 0
        val receiver = ProfileLifecycleInvalidationReceiver { invalidations++ }

        ProfileLifecycleInvalidationReceiver.supportedActions.forEachIndexed { index, action ->
            val before = invalidations
            receiver.onReceive(null, Intent(action))
            assertEquals("Supported action $action (index $index)", before + 1, invalidations)
        }

        val beforeUnrelated = invalidations
        receiver.onReceive(null, Intent("me.foxtails.lakehead.UNRELATED"))
        assertEquals(beforeUnrelated, invalidations)

        receiver.onReceive(null, null)
        assertEquals(beforeUnrelated, invalidations)
    }
}
