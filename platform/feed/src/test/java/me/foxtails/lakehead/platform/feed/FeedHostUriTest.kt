package me.foxtails.lakehead.platform.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FeedHostUri], the pure-Kotlin builder for the host data URI that
 * overlay provider intent-filters match on.
 *
 * No Android framework types are used: scheme, authority, and parameter extraction
 * use plain string operations because `android.net.Uri` returns defaults in unit tests.
 */
class FeedHostUriTest {

    @Test
    fun buildProducesExactUriForTypicalHost() {
        val uri = FeedHostUri.build("me.foxtails.lakehead", 10000)

        assertEquals("app://me.foxtails.lakehead:10000?v=7&cv=9", uri)
    }

    @Test
    fun schemeIsApp() {
        val uri = FeedHostUri.build("com.example", 0)

        // scheme is everything before "://"
        val scheme = uri.substringBefore("://")
        assertEquals(FeedHostUri.SCHEME, scheme)
        assertEquals("app", scheme)
    }

    @Test
    fun authorityContainsPackageAndUid() {
        val uri = FeedHostUri.build("com.example.host", 12345)

        // authority is between "://" and "?"
        val authority = uri.substringAfter("://").substringBefore("?")
        assertEquals("com.example.host:12345", authority)
        assertTrue(authority.contains("com.example.host"))
        assertTrue(authority.contains("12345"))
    }

    @Test
    fun versionParamIsSeven() {
        val uri = FeedHostUri.build("com.example", 0)

        val query = uri.substringAfter("?")
        val params = query.split("&").associate {
            val (k, v) = it.split("=")
            k to v
        }
        assertEquals("7", params["v"])
    }

    @Test
    fun clientVersionParamIsNine() {
        val uri = FeedHostUri.build("com.example", 0)

        val query = uri.substringAfter("?")
        val params = query.split("&").associate {
            val (k, v) = it.split("=")
            k to v
        }
        assertEquals("9", params["cv"])
    }

    @Test
    fun uriWithZeroUid() {
        val uri = FeedHostUri.build("com.example", 0)

        assertEquals("app://com.example:0?v=7&cv=9", uri)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPackageThrows() {
        FeedHostUri.build("", 10000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whitespaceOnlyPackageThrows() {
        FeedHostUri.build("   ", 10000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeUidThrows() {
        FeedHostUri.build("com.example", -1)
    }

    @Test
    fun schemeConstantMatchesUriScheme() {
        // The object's SCHEME constant is what providers filter on; it must be "app".
        assertEquals("app", FeedHostUri.SCHEME)
    }

    @Test
    fun buildWithMaxIntUidDoesNotOverflow() {
        val uri = FeedHostUri.build("com.example", Int.MAX_VALUE)

        assertTrue(uri.contains(":${Int.MAX_VALUE}?"))
    }
}
