package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutJsonTest {

    private fun makePlacement(
        id: String,
        column: Int,
        row: Int,
        columns: Int = 1,
        rows: Int = 1,
        profileId: String = "0",
        packageName: String = "com.example",
        className: String = "com.example.Main",
    ) = AppPlacement(
        homeId = HomeId("home-main"),
        itemId = ItemId(id),
        appIdentity = AppIdentity(
            profileId = ProfileId(profileId),
            component = ComponentIdentity(packageName, className),
        ),
        position = LogicalPosition(column, row),
        span = LogicalSpan(columns, rows),
        availability = ItemAvailability.AVAILABLE,
    )

    // --- Round-trip tests ---

    @Test
    fun roundTrip_emptyLayout() {
        val original = emptyList<AppPlacement>()
        val bindings = emptyMap<ItemId, TileBinding>()
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, original, bindings)
        val doc = HomeLayoutCodec.decodeLayout(json)

        assertEquals("home-main", doc.homeId.value)
        assertEquals(5, doc.columns)
        assertTrue(doc.items.isEmpty())
    }

    @Test
    fun roundTrip_singlePlacement() {
        val placement = makePlacement("tile-1", column = 2, row = 0)
        val bindings = mapOf(ItemId("tile-1") to TileBinding(ItemId("tile-1")))
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, listOf(placement), bindings)
        val doc = HomeLayoutCodec.decodeLayout(json)

        assertEquals(1, doc.items.size)
        val item = doc.items[0]
        assertEquals("tile-1", item.id)
        assertEquals("APP", item.type)
        assertEquals(2, item.column)
        assertEquals(0, item.row)
        assertEquals(1, item.columns)
        assertEquals(1, item.rows)
        assertEquals("0", item.app.profileId)
        assertEquals("com.example", item.app.packageName)
        assertEquals("com.example.Main", item.app.className)
        assertTrue(item.pinnedShortcuts.isEmpty())
    }

    @Test
    fun roundTrip_multiplePlacements() {
        val placements = listOf(
            makePlacement("a", column = 0, row = 0, columns = 2, rows = 1),
            makePlacement("b", column = 3, row = 0),
            makePlacement("c", column = 0, row = 1, columns = 1, rows = 2),
        )
        val bindings = emptyMap<ItemId, TileBinding>()
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-test"), 5, placements, bindings)
        val doc = HomeLayoutCodec.decodeLayout(json)

        assertEquals("home-test", doc.homeId.value)
        assertEquals(3, doc.items.size)
        assertEquals("a", doc.items[0].id)
        assertEquals(2, doc.items[0].columns)
        assertEquals("b", doc.items[1].id)
        assertEquals("c", doc.items[2].id)
        assertEquals(2, doc.items[2].rows)
    }

    @Test
    fun roundTrip_withPinnedShortcuts() {
        val placement = makePlacement("tile-1", column = 0, row = 0)
        val binding = TileBinding(
            tileId = ItemId("tile-1"),
            pinnedShortcuts = listOf(
                ShortcutRef("com.example", ProfileId("0"), "sc-1"),
                ShortcutRef("com.other", ProfileId("10"), "sc-2"),
            ),
        )
        val bindings = mapOf(ItemId("tile-1") to binding)
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, listOf(placement), bindings)
        val doc = HomeLayoutCodec.decodeLayout(json)

        assertEquals(1, doc.items.size)
        val shortcuts = doc.items[0].pinnedShortcuts
        assertEquals(2, shortcuts.size)
        assertEquals("com.example", shortcuts[0].packageName)
        assertEquals("0", shortcuts[0].profileId)
        assertEquals("sc-1", shortcuts[0].shortcutId)
        assertEquals("com.other", shortcuts[1].packageName)
        assertEquals("10", shortcuts[1].profileId)
        assertEquals("sc-2", shortcuts[1].shortcutId)
    }

    @Test
    fun roundTrip_shortcutOrderPreserved() {
        val placement = makePlacement("tile-1", column = 0, row = 0)
        val shortcuts = listOf(
            ShortcutRef("com.a", ProfileId("0"), "first"),
            ShortcutRef("com.b", ProfileId("0"), "second"),
            ShortcutRef("com.c", ProfileId("0"), "third"),
        )
        val binding = TileBinding(tileId = ItemId("tile-1"), pinnedShortcuts = shortcuts)
        val json = HomeLayoutCodec.encodeLayout(
            HomeId("home-main"), 5, listOf(placement), mapOf(ItemId("tile-1") to binding),
        )
        val doc = HomeLayoutCodec.decodeLayout(json)

        val decoded = doc.items[0].pinnedShortcuts
        assertEquals("first", decoded[0].shortcutId)
        assertEquals("second", decoded[1].shortcutId)
        assertEquals("third", decoded[2].shortcutId)
    }

    @Test
    fun roundTrip_mixedProfiles() {
        val placement = makePlacement(
            "tile-1", column = 0, row = 0,
            profileId = "10",
            packageName = "com.work.app",
            className = "com.work.app.Activity",
        )
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, listOf(placement), emptyMap())
        val doc = HomeLayoutCodec.decodeLayout(json)

        assertEquals("10", doc.items[0].app.profileId)
        assertEquals("com.work.app", doc.items[0].app.packageName)
    }

    @Test
    fun encodeLayout_containsVersionAndKind() {
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, emptyList(), emptyMap())
        assertEquals(1, json.getInt("version"))
        assertEquals("proto-launch.home-layout/v1", json.getString("kind"))
        assertTrue(json.has("exportedAtMillis"))
    }

    @Test
    fun encodeLayout_homeSectionContainsIdAndColumns() {
        val json = HomeLayoutCodec.encodeLayout(HomeId("home-main"), 5, emptyList(), emptyMap())
        val home = json.getJSONObject("home")
        assertEquals("home-main", home.getString("id"))
        assertEquals(5, home.getInt("columns"))
    }

    // --- Rejection tests ---

    @Test(expected = HomeLayoutJsonException::class)
    fun decodeLayout_missingVersion_throws() {
        val json = JSONObject().apply {
            put("kind", "proto-launch.home-layout/v1")
            put("home", JSONObject().apply {
                put("id", "home-main")
                put("columns", 5)
                put("items", JSONArray())
            })
        }
        HomeLayoutCodec.decodeLayout(json)
    }

    @Test
    fun decodeLayout_wrongVersion_throws() {
        for (badVersion in listOf(2, 0, -1)) {
            val json = JSONObject().apply {
                put("version", badVersion)
                put("kind", "proto-launch.home-layout/v1")
                put("home", JSONObject().apply {
                    put("id", "home-main")
                    put("columns", 5)
                    put("items", JSONArray())
                })
            }
            try {
                HomeLayoutCodec.decodeLayout(json)
                throw AssertionError("Expected exception for version $badVersion")
            } catch (e: HomeLayoutJsonException) {
                assertTrue(
                    "Expected UnknownVersion for $badVersion",
                    e.reason is RejectedReason.UnknownVersion,
                )
            }
        }
    }

    @Test
    fun decodeLayout_wrongKind_throws() {
        val json = JSONObject().apply {
            put("version", 1)
            put("kind", "wrong.kind/v1")
            put("home", JSONObject().apply {
                put("id", "home-main")
                put("columns", 5)
                put("items", JSONArray())
            })
        }
        try {
            HomeLayoutCodec.decodeLayout(json)
            throw AssertionError("Expected exception")
        } catch (e: HomeLayoutJsonException) {
            assertTrue(e.reason is RejectedReason.UnknownKind)
        }
    }

    @Test
    fun decodeLayout_blankHomeId_throws() {
        val json = JSONObject().apply {
            put("version", 1)
            put("kind", "proto-launch.home-layout/v1")
            put("home", JSONObject().apply {
                put("id", "")
                put("columns", 5)
                put("items", JSONArray())
            })
        }
        try {
            HomeLayoutCodec.decodeLayout(json)
            throw AssertionError("Expected exception")
        } catch (e: HomeLayoutJsonException) {
            assertTrue(e.reason is RejectedReason.MissingField)
        }
    }

    @Test
    fun decodeLayout_missingColumns_throws() {
        val json = JSONObject().apply {
            put("version", 1)
            put("kind", "proto-launch.home-layout/v1")
            put("home", JSONObject().apply {
                put("id", "home-main")
                put("items", JSONArray())
            })
        }
        try {
            HomeLayoutCodec.decodeLayout(json)
            throw AssertionError("Expected exception")
        } catch (e: HomeLayoutJsonException) {
            assertTrue(e.reason is RejectedReason.MissingField)
        }
    }

    @Test
    fun decodeLayout_itemMissingId_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_itemMissingType_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_unknownType_decodedWithoutThrow() {
        // Unknown types are accepted by the codec; the import coordinator
        // rejects them per-item so a single unknown type doesn't invalidate
        // the entire document.
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "FOLDER")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        val doc = HomeLayoutCodec.decodeLayout(json)
        assertEquals(1, doc.items.size)
        assertEquals("FOLDER", doc.items[0].type)
    }

    @Test
    fun decodeLayout_negativeColumn_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", -1); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_negativeRow_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", -1)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_nonPositiveColumns_throws() {
        for (bad in listOf(0, -1)) {
            val json = makeValidDoc().apply {
                getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                    put("id", "tile-1")
                    put("type", "APP")
                    put("column", 0); put("row", 0)
                    put("columns", bad); put("rows", 1)
                    put("app", JSONObject().apply {
                        put("profileId", "0")
                        put("packageName", "com.example")
                        put("className", "com.example.Main")
                    })
                })
            }
            try {
                HomeLayoutCodec.decodeLayout(json)
                throw AssertionError("Expected exception for columns=$bad")
            } catch (e: HomeLayoutJsonException) {
                assertTrue(e.reason is RejectedReason.InvalidSpan)
            }
        }
    }

    @Test
    fun decodeLayout_nonPositiveRows_throws() {
        for (bad in listOf(0, -1)) {
            val json = makeValidDoc().apply {
                getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                    put("id", "tile-1")
                    put("type", "APP")
                    put("column", 0); put("row", 0)
                    put("columns", 1); put("rows", bad)
                    put("app", JSONObject().apply {
                        put("profileId", "0")
                        put("packageName", "com.example")
                        put("className", "com.example.Main")
                    })
                })
            }
            try {
                HomeLayoutCodec.decodeLayout(json)
                throw AssertionError("Expected exception for rows=$bad")
            } catch (e: HomeLayoutJsonException) {
                assertTrue(e.reason is RejectedReason.InvalidSpan)
            }
        }
    }

    @Test
    fun decodeLayout_appMissingProfileId_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_appMissingPackageName_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_appMissingClassName_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_shortcutMissingPackageName_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
                put("pinnedShortcuts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("profileId", "0")
                        put("shortcutId", "sc-1")
                    })
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_shortcutMissingProfileId_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
                put("pinnedShortcuts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("packageName", "com.example")
                        put("shortcutId", "sc-1")
                    })
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_shortcutMissingShortcutId_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
                put("pinnedShortcuts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("packageName", "com.example")
                        put("profileId", "0")
                    })
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_itemMissingColumn_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    @Test
    fun decodeLayout_itemMissingApp_throws() {
        val json = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "APP")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
            })
        }
        assertItemRejected(json, RejectedReason.MissingField::class.java)
    }

    // --- Helpers ---

    /** Creates a valid document with zero items for mutation in tests. */
    private fun makeValidDoc() = JSONObject().apply {
        put("version", 1)
        put("kind", "proto-launch.home-layout/v1")
        put("exportedAtMillis", 1730000000000L)
        put("home", JSONObject().apply {
            put("id", "home-main")
            put("columns", 5)
            put("items", JSONArray())
        })
    }

    /**
     * Asserts that decoding [json] throws [HomeLayoutJsonException] whose
     * reason is an instance of [expectedReasonClass].
     */
    private fun assertItemRejected(json: JSONObject, expectedReasonClass: Class<out RejectedReason>) {
        try {
            HomeLayoutCodec.decodeLayout(json)
            throw AssertionError("Expected HomeLayoutJsonException")
        } catch (e: HomeLayoutJsonException) {
            assertTrue(
                "Expected ${expectedReasonClass.simpleName} but got ${e.reason::class.simpleName}",
                expectedReasonClass.isInstance(e.reason),
            )
        }
    }
}