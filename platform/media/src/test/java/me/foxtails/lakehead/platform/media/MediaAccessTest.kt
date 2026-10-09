package me.foxtails.lakehead.platform.media

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {

    @Test
    fun permissionsFor_requestsImagesOn33AndStorageBelow() {
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES),
            MediaAccessQuery.permissionsFor(Build.VERSION_CODES.TIRAMISU),
        )
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES),
            MediaAccessQuery.permissionsFor(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            MediaAccessQuery.permissionsFor(Build.VERSION_CODES.S),
        )
    }

    @Test
    fun accessFrom34_imagesGrantIsFull() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(
            MediaAccess.FULL,
            MediaAccessQuery.accessFrom(setOf(Manifest.permission.READ_MEDIA_IMAGES), sdk),
        )
        assertEquals(
            MediaAccess.FULL,
            MediaAccessQuery.accessFrom(
                setOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                ),
                sdk,
            ),
        )
    }

    @Test
    fun accessFrom34_userSelectedAloneIsPartial() {
        assertEquals(
            MediaAccess.PARTIAL,
            MediaAccessQuery.accessFrom(
                setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ),
        )
    }

    @Test
    fun accessFrom34_nothingGrantedIsDenied() {
        assertEquals(
            MediaAccess.DENIED,
            MediaAccessQuery.accessFrom(emptySet(), Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
    }

    @Test
    fun accessFrom33_userSelectedAloneIsDenied() {
        assertEquals(
            MediaAccess.DENIED,
            MediaAccessQuery.accessFrom(
                setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
                Build.VERSION_CODES.TIRAMISU,
            ),
        )
        assertEquals(
            MediaAccess.FULL,
            MediaAccessQuery.accessFrom(
                setOf(Manifest.permission.READ_MEDIA_IMAGES),
                Build.VERSION_CODES.TIRAMISU,
            ),
        )
        assertEquals(
            MediaAccess.DENIED,
            MediaAccessQuery.accessFrom(emptySet(), Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun accessFrom32_storageGrantIsFullAndImagesGrantIsDenied() {
        val sdk = Build.VERSION_CODES.S
        assertEquals(
            MediaAccess.FULL,
            MediaAccessQuery.accessFrom(setOf(Manifest.permission.READ_EXTERNAL_STORAGE), sdk),
        )
        assertEquals(
            MediaAccess.DENIED,
            MediaAccessQuery.accessFrom(setOf(Manifest.permission.READ_MEDIA_IMAGES), sdk),
        )
        assertEquals(MediaAccess.DENIED, MediaAccessQuery.accessFrom(emptySet(), sdk))
    }
}
