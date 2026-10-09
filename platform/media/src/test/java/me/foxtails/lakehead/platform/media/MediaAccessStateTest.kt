package me.foxtails.lakehead.platform.media

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Live [MediaAccessQuery.accessNow] state: denied with no grants, full once the
 * images permission is granted. The partial branch is covered by the pure
 * [MediaAccessTest] decision tests; API 34+ partial needs a real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaAccessStateTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun accessNow_isDeniedWithNoGrants() {
        assertEquals(MediaAccess.DENIED, MediaAccessQuery.accessNow(context))
    }

    @Test
    fun accessNow_isFullOnceImagesPermissionIsGranted() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)

        assertEquals(MediaAccess.FULL, MediaAccessQuery.accessNow(context))
    }
}
