package me.foxtails.lakehead.platform.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppWidgetProviderCatalogueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun refresh_listsEveryProviderTheSystemReportsForThePersonalProfile() {
        val catalogue = AppWidgetProviderCatalogue(context)
        runBlocking { catalogue.refresh() }
        val listed = catalogue.providers.value

        val serial = context.getSystemService(UserManager::class.java)
            .getSerialNumberForUser(Process.myUserHandle()).toString()
        val expected = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForProfile(Process.myUserHandle())
            .map { it.provider.packageName to it.provider.className }
            .toSet()
        val actual = listed.filter { it.provider.profileId.value == serial }
            .map { it.provider.component.packageName to it.provider.component.className }
            .toSet()

        assertTrue("the device should expose at least one widget provider", expected.isNotEmpty())
        assertEquals(expected, actual)
        assertTrue(listed.all { it.label.isNotBlank() && it.appLabel.isNotBlank() })
    }
}
