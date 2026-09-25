package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileTokenAdapterTest {
    @Test
    fun accessibleProfilesRoundTripAndHaveDistinctTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val adapter = ProfileTokenAdapter(launcherApps, userManager)
        val handles = launcherApps.profiles
        assertTrue("LauncherApps should expose at least the calling profile", handles.isNotEmpty())

        val ids = handles.map { handle ->
            val id = adapter.profileIdFor(handle)
            assertNotNull("Every accessible handle must have a serial token", id)
            assertEquals("Token must resolve to the original handle", handle, adapter.userFor(id!!))
            id
        }
        assertEquals("Distinct profile handles must have distinct tokens", handles.distinct().size, ids.distinct().size)

        assertNull(adapter.userFor(ProfileId("-1")))
        assertNull(adapter.userFor(ProfileId("01")))
        assertNull(adapter.userFor(ProfileId("not-a-serial")))
    }
}
