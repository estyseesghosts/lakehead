package me.foxtails.lakehead.platform.notifications

import me.foxtails.lakehead.core.model.NotificationSourceKey
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTrackerTest {
    private val chat = NotificationSourceKey("com.chat", ProfileId("0"))
    private val workChat = NotificationSourceKey("com.chat", ProfileId("10"))

    private fun facts(
        id: String,
        source: NotificationSourceKey = chat,
        postTime: Long = 1,
        ongoing: Boolean = false,
        summary: Boolean = false,
        sender: String? = null,
        conversation: String? = null,
        title: String? = null,
        text: String? = "hi",
    ) = NotificationFacts(id, source, postTime, ongoing, summary, sender, conversation, title, text)

    private fun tracked(f: NotificationFacts) = normalizeNotification(f)!!

    @Test
    fun ongoingAndGroupSummariesNeverTrack() {
        assertNull(normalizeNotification(facts("a", ongoing = true)))
        assertNull(normalizeNotification(facts("b", summary = true)))
    }

    @Test
    fun nameFallsBackSenderThenConversationThenTitle() {
        assertEquals("Ann", tracked(facts("a", sender = "Ann", conversation = "Group", title = "T")).name)
        assertEquals("Group", tracked(facts("b", sender = " ", conversation = "Group", title = "T")).name)
        assertEquals("T", tracked(facts("c", title = "T")).name)
        assertEquals("", tracked(facts("d")).name)
    }

    @Test
    fun countsPerPackageAndProfile() {
        val tracker = NotificationTracker()
        tracker.replaceAll(
            listOf(
                tracked(facts("1", title = "a")),
                tracked(facts("2", title = "b")),
                tracked(facts("3", source = workChat, title = "c")),
            ),
        )
        val summaries = tracker.summaries()
        assertEquals(2, summaries.getValue(chat).count)
        assertEquals(1, summaries.getValue(workChat).count)
    }

    @Test
    fun previewsAreNewestFirstAndCapped() {
        val tracker = NotificationTracker(maxPreviews = 2)
        (1..4).forEach { tracker.upsert(tracked(facts("$it", postTime = it.toLong(), title = "n$it"))) }
        val summary = tracker.summaries().getValue(chat)
        assertEquals(4, summary.count)
        assertEquals(listOf("n4", "n3"), summary.previews.map { it.name })
    }

    @Test
    fun emptyContentCountsButHasNoPreview() {
        val tracker = NotificationTracker()
        tracker.upsert(tracked(facts("1", text = null)))
        val summary = tracker.summaries().getValue(chat)
        assertEquals(1, summary.count)
        assertEquals(0, summary.previews.size)
    }

    @Test
    fun removeAndClearDropSources() {
        val tracker = NotificationTracker()
        tracker.upsert(tracked(facts("1", title = "a")))
        tracker.remove("1")
        assertEquals(emptyMap<NotificationSourceKey, Any>(), tracker.summaries())
        tracker.upsert(tracked(facts("2", title = "b")))
        tracker.clear()
        assertEquals(0, tracker.summaries().size)
    }

    @Test
    fun repostKeepsAttachedPhoto() {
        val tracker = NotificationTracker()
        tracker.upsert(tracked(facts("1", title = "a")))
        tracker.attachPhoto("1", "photo-1")
        tracker.upsert(tracked(facts("1", title = "a", text = "updated")))
        assertEquals("photo-1", tracker.summaries().getValue(chat).previews.single().photoKey)
        tracker.attachPhoto("missing", "x")
        assertEquals(setOf("photo-1"), tracker.photoKeys())
    }
}
