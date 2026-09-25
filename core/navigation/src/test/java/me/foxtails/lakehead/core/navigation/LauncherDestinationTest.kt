package me.foxtails.lakehead.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDestinationTest {

    @Test
    fun feedIsStub() {
        assertTrue(LauncherDestination.FEED.isStub)
    }

    @Test
    fun notificationsIsStub() {
        assertTrue(LauncherDestination.NOTIFICATIONS.isStub)
    }

    @Test
    fun homeIsNotStub() {
        assertFalse(LauncherDestination.HOME.isStub)
    }

    @Test
    fun allAppsIsNotStub() {
        assertFalse(LauncherDestination.ALL_APPS.isStub)
    }

    @Test
    fun allFourDestinationsExist() {
        val values = LauncherDestination.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(LauncherDestination.FEED))
        assertTrue(values.contains(LauncherDestination.NOTIFICATIONS))
        assertTrue(values.contains(LauncherDestination.HOME))
        assertTrue(values.contains(LauncherDestination.ALL_APPS))
    }
}