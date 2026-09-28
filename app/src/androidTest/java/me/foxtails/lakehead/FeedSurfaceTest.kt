package me.foxtails.lakehead

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedProviderId
import me.foxtails.lakehead.platform.feed.FeedProviderResolver
import me.foxtails.lakehead.platform.feed.FeedServiceComponent
import me.foxtails.lakehead.platform.feed.FeedServiceProbeResult
import me.foxtails.lakehead.platform.feed.LAWNFEED_LEGACY_PACKAGE
import me.foxtails.lakehead.platform.feed.LAWNFEED_PACKAGE
import me.foxtails.lakehead.platform.feed.NEO_FEED_PACKAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D1 feed-surface gap coverage for the [FeedSurface] host-boundary seam.
 *
 * - The [FEED_SURFACE_TAG] slot exists in both availability states.
 * - `Unavailable` keeps the honest "Feed unavailable" fallback text.
 * - `Available` hosts the provider slot with no fallback text, for both
 *   [FeedProviderId.Lawnfeed] and [FeedProviderId.NeoFeed].
 * - Resolver priority (hermetic [FeedProviderResolver] fakes, no provider
 *   install required): both providers answer -> Lawnfeed; each alone resolves
 *   to itself; legacy Lawnfeed id still resolves; none answers ->
 *   `Unavailable(NO_PROVIDER)`.
 * - Lifecycle / window handshake: one attach per hosted provider identity
 *   (never per recomposition), provider switch re-attaches without tearing
 *   down the reused host view, leaving the page detaches. The shell forwards progress,
 *   visibility and the window handshake (the seam `MainActivity` binds to the
 *   gateway's never-throw notify verbs); `Unavailable` never attaches.
 * - Left-surface pager: page 0 ([LAUNCHER_PAGE_FEED]) renders the host tag
 *   when a provider is available and the honest fallback when `NO_PROVIDER`,
 *   and the shell keeps its 3-page pill (Feed/Home/All Apps) either way.
 */
@RunWith(AndroidJUnit4::class)
class FeedSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val unavailable = FeedAvailability.Unavailable(
        FeedAvailability.Unavailable.Reason.NO_PROVIDER,
    )
    private val lawnfeedAvailable = FeedAvailability.Available(FeedProviderId.Lawnfeed)
    private val neoFeedAvailable = FeedAvailability.Available(FeedProviderId.NeoFeed)

    @Test
    fun unavailable_showsFallbackTextInTaggedSlot() {
        composeRule.setContent {
            FeedSurface(availability = unavailable)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertExists()
        composeRule.onNodeWithText("Feed unavailable").assertIsDisplayed()
    }

    @Test
    fun available_hostsProviderSlotWithoutFallbackText() {
        composeRule.setContent {
            FeedSurface(availability = lawnfeedAvailable)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertExists()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
    }

    @Test
    fun shell_availableFeed_keepsThreePagePill() {
        composeRule.setContent {
            LauncherShell(
                homeState = testHomeState(),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = lawnfeedAvailable,
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        // Provider page hosts the slot, not the fallback ...
        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
        // ... and the 3-page pill is unchanged.
        composeRule.onNodeWithContentDescription("Feed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All Apps").assertIsDisplayed()
    }

    // --- Resolver priority: hermetic fakes, no provider install required. ---

    @Test
    fun resolver_bothProviders_prefersLawnfeed() {
        val resolver = FeedProviderResolver { packageName, _ ->
            FeedServiceProbeResult.present(
                FeedServiceComponent(packageName, "$packageName.FeedService"),
            )
        }

        val result = resolver.resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), result)
    }

    @Test
    fun resolver_lawnfeedOnly_resolvesLawnfeed() {
        val resolver = FeedProviderResolver { packageName, _ ->
            if (packageName == LAWNFEED_PACKAGE) {
                FeedServiceProbeResult.present(
                    FeedServiceComponent(packageName, "$packageName.FeedService"),
                )
            } else {
                FeedServiceProbeResult.absent()
            }
        }

        val result = resolver.resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), result)
    }

    @Test
    fun resolver_legacyLawnfeedId_resolvesLawnfeed() {
        val resolver = FeedProviderResolver { packageName, _ ->
            if (packageName == LAWNFEED_LEGACY_PACKAGE) {
                FeedServiceProbeResult.present(
                    FeedServiceComponent(packageName, "$packageName.FeedService"),
                )
            } else {
                FeedServiceProbeResult.absent()
            }
        }

        val result = resolver.resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.Lawnfeed), result)
    }

    @Test
    fun resolver_neoFeedOnly_resolvesNeoFeed() {
        val resolver = FeedProviderResolver { packageName, _ ->
            if (packageName == NEO_FEED_PACKAGE) {
                FeedServiceProbeResult.present(
                    FeedServiceComponent(packageName, "$packageName.FeedService"),
                )
            } else {
                FeedServiceProbeResult.absent()
            }
        }

        val result = resolver.resolve()

        assertEquals(FeedAvailability.Available(FeedProviderId.NeoFeed), result)
    }

    @Test
    fun resolver_noProvider_reportsNoProvider() {
        val resolver = FeedProviderResolver { _, _ -> FeedServiceProbeResult.absent() }

        val result = resolver.resolve()

        assertEquals(
            FeedAvailability.Unavailable(
                FeedAvailability.Unavailable.Reason.NO_PROVIDER,
            ),
            result,
        )
    }

    // --- Provider surfaces: each provider hosts the overlay tag. ---

    @Test
    fun lawnfeed_hostsOverlayTagWithoutFallback() {
        composeRule.setContent {
            FeedSurface(availability = lawnfeedAvailable)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
    }

    @Test
    fun neoFeed_hostsOverlayTagWithoutFallback() {
        composeRule.setContent {
            FeedSurface(availability = neoFeedAvailable)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
    }

    @Test
    fun overlaySurface_nullProvider_hostsNothing() {
        composeRule.setContent {
            FeedOverlaySurface(provider = null)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertDoesNotExist()
    }

    // --- Lifecycle / window handshake (hermetic: counted callbacks). ---

    @Test
    fun feedSurface_windowHandshake_attachOncePerProvider_detachOnRelease() {
        var availability: FeedAvailability by mutableStateOf(lawnfeedAvailable)
        var attached = 0
        var detached = 0
        composeRule.setContent {
            FeedSurface(
                availability = availability,
                onWindowAttached = { attached += 1 },
                onWindowDetached = { detached += 1 },
            )
        }
        composeRule.waitForIdle()

        // One registration for the hosted identity ...
        assertEquals(1, attached)
        assertEquals(0, detached)
        // ... stable across idle recomposition (pager scroll frames must not
        // re-register the window).
        composeRule.waitForIdle()
        assertEquals(1, attached)

        // Provider switch re-attaches the new identity. The host AndroidView is
        // reused across the switch (same call site still emits AndroidView),
        // so no release fires — only the window registration refreshes.
        composeRule.runOnUiThread { availability = neoFeedAvailable }
        composeRule.waitForIdle()
        assertEquals(2, attached)
        assertEquals(0, detached)

        // Leaving the page releases the hosted view.
        composeRule.runOnUiThread { availability = unavailable }
        composeRule.waitForIdle()
        assertEquals(2, attached)
        assertEquals(1, detached)
        composeRule.onNodeWithText("Feed unavailable").assertIsDisplayed()
    }

    @Test
    fun shell_forwardsFeedDrivingAndWindowHandshake() {
        val progressValues = mutableListOf<Float>()
        val visibleValues = mutableListOf<Boolean>()
        var attached = 0
        var detached = 0
        composeRule.setContent {
            LauncherShell(
                homeState = testHomeState(),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = lawnfeedAvailable,
                onFeedProgress = { progressValues += it },
                onFeedVisible = { visibleValues += it },
                onFeedWindowAttached = { attached += 1 },
                onFeedWindowDetached = { detached += 1 },
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        // Settled on the feed page: progress 1, visible true, window attached
        // once (production forwards these to the gateway's never-throw
        // notify verbs — see MainActivity wiring).
        assertTrue(progressValues.isNotEmpty())
        assertEquals(1f, progressValues.last())
        assertTrue(visibleValues.isNotEmpty())
        assertTrue(visibleValues.last())
        assertEquals(1, attached)
        assertEquals(0, detached)
        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertIsDisplayed()
    }

    @Test
    fun shell_unavailableFeed_neverAttachesWindow() {
        var attached = 0
        var detached = 0
        composeRule.setContent {
            LauncherShell(
                homeState = testHomeState(),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = unavailable,
                onFeedWindowAttached = { attached += 1 },
                onFeedWindowDetached = { detached += 1 },
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, attached)
        assertEquals(0, detached)
        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Feed unavailable").assertIsDisplayed()
    }

    // --- Left-surface pager: feed page per availability. ---

    @Test
    fun shell_neoFeedPage_hostsOverlayAndKeepsPill() {
        composeRule.setContent {
            LauncherShell(
                homeState = testHomeState(),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = neoFeedAvailable,
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Feed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All Apps").assertIsDisplayed()
    }

    @Test
    fun shell_unavailableFeedPage_showsHonestFallbackAndKeepsPill() {
        composeRule.setContent {
            LauncherShell(
                homeState = testHomeState(),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = unavailable,
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        // NO_PROVIDER on the left surface: honest fallback, no host tag ...
        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(FEED_OVERLAY_HOST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Feed unavailable").assertIsDisplayed()
        // ... and the 3-page pill is unchanged.
        composeRule.onNodeWithContentDescription("Feed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All Apps").assertIsDisplayed()
    }

    private fun testHomeState(): HomeRenderState = HomeRenderState.Loaded(
        requestedHomeId = HomeId("home-test"),
        tiles = emptyList(),
        columns = GridColumns(6),
        extent = GridVerticalExtent(4L, 10L),
        window = GridRowWindow(0L, 10L),
    )
}
