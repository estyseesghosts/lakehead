package me.foxtails.lakehead.platform.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Denied-path device proof. The test revokes both photo permissions from
 * itself through the shell first, so it is order-independent: it passes
 * whether or not the granted tests ran before it.
 */
@RunWith(AndroidJUnit4::class)
class MediaAccessDeniedDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deniedAccess_reportsDeniedAndYieldsNothing(): Unit = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "pm revoke ${context.packageName} android.permission.READ_MEDIA_IMAGES",
        ).close()
        automation.executeShellCommand(
            "pm revoke ${context.packageName} android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ).close()

        assertEquals(MediaAccess.DENIED, MediaAccessQuery.accessNow(context))

        val gateway = MediaStoreGateway(context)
        assertTrue(gateway.albums().isEmpty())
        assertTrue(gateway.photos("any-bucket").isEmpty())
        assertNull(gateway.thumbnail("content://media/external/images/media/1", 96))
    }
}
