package me.foxtails.lakehead

import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedDiagnostic
import me.foxtails.lakehead.platform.feed.FeedDiagnosticCause
import me.foxtails.lakehead.platform.feed.FeedDiagnosticStatus
import me.foxtails.lakehead.platform.feed.FeedProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-helper tests for the S1 settings menu selection contract.
 *
 * [selectedFeedProvider] maps [FeedAvailability.Available] to its exact provider and every
 * [FeedAvailability.Unavailable] reason to no selection; row labels and tags derive from
 * [FeedProviderId.PRIORITY] identity. No composition, binding or persistence here.
 */
class SettingsMenuSelectionTest {

    @Test
    fun availableLawnfeed_selectsLawnfeed() {
        assertEquals(
            FeedProviderId.Lawnfeed,
            selectedFeedProvider(FeedAvailability.Available(FeedProviderId.Lawnfeed)),
        )
    }

    @Test
    fun availableNeoFeed_selectsNeoFeed() {
        assertEquals(
            FeedProviderId.NeoFeed,
            selectedFeedProvider(FeedAvailability.Available(FeedProviderId.NeoFeed)),
        )
    }

    @Test
    fun unavailable_selectsNothingForEveryReason() {
        for (reason in FeedAvailability.Unavailable.Reason.entries) {
            assertNull(
                "Unavailable($reason) must select nothing",
                selectedFeedProvider(FeedAvailability.Unavailable(reason)),
            )
        }
    }

    @Test
    fun rows_coverPriorityInOrder() {
        assertEquals(
            listOf(FeedProviderId.Lawnfeed, FeedProviderId.NeoFeed),
            FeedProviderId.PRIORITY,
        )
        assertEquals("Lawnfeed", feedProviderLabel(FeedProviderId.Lawnfeed))
        assertEquals("Neo Feed", feedProviderLabel(FeedProviderId.NeoFeed))
        assertEquals(
            "feed-provider-row-lawnfeed",
            feedProviderRowTag(FeedProviderId.Lawnfeed),
        )
        assertEquals(
            "feed-provider-row-neofeed",
            feedProviderRowTag(FeedProviderId.NeoFeed),
        )
    }

    @Test
    fun diagnosticFooter_readsStatusAndCause() {
        assertNull(feedDiagnosticFooterText(null))
        assertEquals(
            "NO_PROVIDER · ALL_ABSENT",
            feedDiagnosticFooterText(
                FeedDiagnostic(FeedDiagnosticStatus.NO_PROVIDER, FeedDiagnosticCause.ALL_ABSENT),
            ),
        )
        assertEquals(
            "BIND_FAILED · NULL_BINDING",
            feedDiagnosticFooterText(
                FeedDiagnostic(FeedDiagnosticStatus.BIND_FAILED, FeedDiagnosticCause.NULL_BINDING),
            ),
        )
    }

    @Test
    fun unavailableReasonText_isHonestPerReason() {
        assertEquals(
            "No feed provider installed",
            feedUnavailableReasonText(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
        )
        assertEquals(
            "Feed query was denied",
            feedUnavailableReasonText(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED),
        )
        assertEquals(
            "Feed query failed",
            feedUnavailableReasonText(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
        )
    }
}
