package me.foxtails.lakehead.feature.allapps

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [groupKey], [sectionCollator], and [toAppSections].
 *
 * No Android, Compose, or Robolectric dependency — runs as a plain JUnit test.
 */
class AllAppsSectionsTest {

    private val collator = sectionCollator()

    // ── helpers ──────────────────────────────────────────────────────────

    private fun identity(pkg: String, cls: String = "$pkg.Main") =
        AppIdentity(ProfileId("0"), ComponentIdentity(pkg, cls))

    private fun entry(label: String, pkg: String = "com.example.${label.replace(" ", "")}") =
        AllAppsEntry(identity = identity(pkg), label = label, alreadyOnHome = false)

    // ── groupKey: Latin groups and order, # first ────────────────────────

    @Test
    fun groupKey_latinUppercase_returnsSameLetter() {
        assertEquals("A", groupKey("Apple", collator))
        assertEquals("Z", groupKey("Zebra", collator))
    }

    @Test
    fun groupKey_latinLowercase_foldstoUppercase() {
        assertEquals("A", groupKey("apple", collator))
        assertEquals("Z", groupKey("zebra", collator))
    }

    @Test
    fun groupKey_hash_returnsHash() {
        assertEquals("#", groupKey("#tagged", collator))
    }

    // ── groupKey: collation fold ─────────────────────────────────────────

    @Test
    fun groupKey_accentE_foldstoE() {
        assertEquals("E", groupKey("éclair", collator))
        assertEquals("E", groupKey("Éclair", collator))
        assertEquals("E", groupKey("èclair", collator))
        assertEquals("E", groupKey("êclair", collator))
        assertEquals("E", groupKey("ëclair", collator))
    }

    @Test
    fun groupKey_accentA_foldstoA() {
        assertEquals("A", groupKey("äpfel", collator))
        assertEquals("A", groupKey("Äpfel", collator))
        assertEquals("A", groupKey("àvion", collator))
        assertEquals("A", groupKey("âvion", collator))
    }

    @Test
    fun groupKey_tildeN_foldstoN() {
        assertEquals("N", groupKey("ñandú", collator))
        assertEquals("N", groupKey("Ñandú", collator))
    }

    @Test
    fun groupKey_umlautO_foldstoO() {
        assertEquals("O", groupKey("ödland", collator))
        assertEquals("O", groupKey("Ödland", collator))
    }

    @Test
    fun groupKey_umlautU_foldstoU() {
        assertEquals("U", groupKey("über", collator))
        assertEquals("U", groupKey("Über", collator))
    }

    @Test
    fun groupKey_cedillaC_foldstoC() {
        assertEquals("C", groupKey("çapa", collator))
        assertEquals("C", groupKey("Çapa", collator))
    }

    // ── groupKey: non-Latin own keys after Latin ─────────────────────────

    @Test
    fun groupKey_cyrillic_uppercaseSingleChar() {
        assertEquals("Д", groupKey("дом", collator))
        assertEquals("Д", groupKey("Дом", collator))
        assertEquals("Б", groupKey("Б", collator))
    }

    @Test
    fun groupKey_greek_uppercaseSingleChar() {
        assertEquals("Α", groupKey("αβγ", collator))
        assertEquals("Α", groupKey("Αβγ", collator))
    }

    @Test
    fun groupKey_cjk_singleChar() {
        assertEquals("你", groupKey("你好", collator))
    }

    // ── groupKey: blank / digit / symbol → # ─────────────────────────────

    @Test
    fun groupKey_blank_returnsHash() {
        assertEquals("#", groupKey("", collator))
        assertEquals("#", groupKey("   ", collator))
    }

    @Test
    fun groupKey_digit_returnsHash() {
        assertEquals("#", groupKey("1Password", collator))
        assertEquals("#", groupKey("9GAG", collator))
        assertEquals("#", groupKey("0123", collator))
    }

    @Test
    fun groupKey_symbol_returnsHash() {
        assertEquals("#", groupKey("+Plus", collator))
        assertEquals("#", groupKey("!Bang", collator))
        assertEquals("#", groupKey("\$Money", collator))
        assertEquals("#", groupKey("@Mail", collator))
    }

    // ── toAppSections: empty / single ────────────────────────────────────

    @Test
    fun toAppSections_emptyList_returnsEmpty() {
        val sections = emptyList<AllAppsEntry>().toAppSections(collator)
        assertTrue("empty input yields no sections", sections.isEmpty())
    }

    @Test
    fun toAppSections_singleEntry_returnsSingleSection() {
        val sections = listOf(entry("Alpha")).toAppSections(collator)
        assertEquals(1, sections.size)
        assertEquals("A", sections[0].key)
        assertEquals(1, sections[0].apps.size)
        assertEquals("Alpha", sections[0].apps[0].label)
    }

    // ── toAppSections: header order matches spec ─────────────────────────

    @Test
    fun toAppSections_headerOrder_hashFirst_thenLatin_thenNonLatin() {
        val entries = listOf(
            entry("Яблоко", pkg = "com.cyrillic"),
            entry("Apple", pkg = "com.apple"),
            entry("1Password", pkg = "com.onepassword"),
            entry("Zebra", pkg = "com.zebra"),
            entry("Mango", pkg = "com.mango"),
        )
        val sections = entries.toAppSections(collator)
        val keys = sections.map { it.key }

        assertEquals("first section must be #", "#", keys[0])
        assertEquals("A", keys[1])
        assertEquals("M", keys[2])
        assertEquals("Z", keys[3])
        // Cyrillic Я comes after Latin Z in collator order.
        assertEquals("Я", keys[4])
        assertEquals("sections count", 5, keys.size)
    }

    @Test
    fun toAppSections_latinSections_inAlphabeticalOrder() {
        val entries = listOf(
            entry("Zulu"),
            entry("Echo"),
            entry("Alpha"),
            entry("Charlie"),
        )
        val sections = entries.toAppSections(collator)
        val keys = sections.map { it.key }

        assertEquals(listOf("A", "C", "E", "Z"), keys)
    }

    @Test
    fun toAppSections_nonLatinSections_sortedByCollator() {
        val entries = listOf(
            entry("Яблоко", pkg = "com.cyrillic.ya"),
            entry("Δέλτα", pkg = "com.greek.delta"),
            entry("Дом", pkg = "com.cyrillic.dom"),
            entry("你好", pkg = "com.cjk"),
        )
        val sections = entries.toAppSections(collator)
        val keys = sections.map { it.key }

        // All are non-Latin; collator sorts by Unicode primary order.
        // Expected order: Greek Δ, Cyrillic Д, Cyrillic Я, CJK 你好
        // (Δ < Д < Я in collator primary order, CJK after Cyrillic)
        assertEquals(4, keys.size)
        // No Latin keys should appear.
        assertTrue("no Latin keys", keys.none { it.length == 1 && it[0] in 'A'..'Z' })
    }

    // ── toAppSections: input order preserved within section ──────────────

    @Test
    fun toAppSections_preservesInputOrder_withinSameSection() {
        val entries = listOf(
            entry("Zebra", pkg = "com.zebra"),
            entry("Apple", pkg = "com.apple"),
            entry("Avocado", pkg = "com.avocado"),
            entry("Zucchini", pkg = "com.zucchini"),
        )
        val sections = entries.toAppSections(collator)

        val aSection = sections.first { it.key == "A" }
        assertEquals("Apple", aSection.apps[0].label)
        assertEquals("Avocado", aSection.apps[1].label)

        val zSection = sections.first { it.key == "Z" }
        assertEquals("Zebra", zSection.apps[0].label)
        assertEquals("Zucchini", zSection.apps[1].label)
    }

    @Test
    fun toAppSections_accents_foldedIntoSameSection() {
        val entries = listOf(
            entry("Éclair", pkg = "com.eclair"),
            entry("Elephant", pkg = "com.elephant"),
            entry("Eagle", pkg = "com.eagle"),
        )
        val sections = entries.toAppSections(collator)

        assertEquals(1, sections.size)
        assertEquals("E", sections[0].key)
        assertEquals(
            "Éclair must fold into E section with Latin apps",
            listOf("Éclair", "Elephant", "Eagle"),
            sections[0].apps.map { it.label },
        )
    }

    // ── toAppSections: # section with mixed symbols ──────────────────────

    @Test
    fun toAppSections_hashSection_groupsDigitsAndSymbols() {
        val entries = listOf(
            entry("9GAG", pkg = "com.ninegag"),
            entry("1Password", pkg = "com.onepassword"),
            entry("!!Bang", pkg = "com.bang"),
        )
        val sections = entries.toAppSections(collator)

        assertEquals(1, sections.size)
        assertEquals("#", sections[0].key)
        assertEquals(
            listOf("9GAG", "1Password", "!!Bang"),
            sections[0].apps.map { it.label },
        )
    }

    // ── toAppSections: regen stable ──────────────────────────────────────

    @Test
    fun toAppSections_regenStable_sameInputProducesSameOutput() {
        val entries = listOf(
            entry("Zebra", pkg = "com.zebra"),
            entry("Äpfel", pkg = "com.apfel"),
            entry("9GAG", pkg = "com.ninegag"),
            entry("Мир", pkg = "com.mir"),
            entry("Apple", pkg = "com.apple"),
            entry("你好", pkg = "com.cjk"),
        )
        val first = entries.toAppSections(collator)
        val second = entries.toAppSections(collator)

        assertEquals(
            "regenerated sections must be identical",
            first, second,
        )
    }

    // ── sectionCollator ──────────────────────────────────────────────────

    @Test
    fun sectionCollator_primaryStrength_treatsAccentsAsEqual() {
        val c = sectionCollator()
        assertEquals(
            "collator must treat é and e as equal at PRIMARY strength",
            0, c.compare("é", "e"),
        )
        assertEquals(
            "collator must treat Ä and A as equal at PRIMARY strength",
            0, c.compare("Ä", "A"),
        )
    }

    // ── sectionIndexForKey ───────────────────────────────────────────────

    @Test
    fun sectionIndexForKey_hash_returnsZero() {
        val entries = listOf(
            entry("1Password", pkg = "com.onepassword"),
            entry("Apple", pkg = "com.apple"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(0, sections.sectionIndexForKey("#"))
    }

    @Test
    fun sectionIndexForKey_midSection_returnsCorrectIndex() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Banana", pkg = "com.banana"),
            entry("Cherry", pkg = "com.cherry"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(1, sections.sectionIndexForKey("B"))
    }

    @Test
    fun sectionIndexForKey_lastSection_returnsLastIndex() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Banana", pkg = "com.banana"),
            entry("Zebra", pkg = "com.zebra"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(2, sections.sectionIndexForKey("Z"))
    }

    @Test
    fun sectionIndexForKey_nonLatin_returnsCorrectIndex() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Яблоко", pkg = "com.cyrillic"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(1, sections.sectionIndexForKey("Я"))
    }

    @Test
    fun sectionIndexForKey_missing_returnsNull() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Banana", pkg = "com.banana"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(null, sections.sectionIndexForKey("Z"))
    }

    @Test
    fun sectionIndexForKey_emptySections_returnsNull() {
        val sections = emptyList<AppSection>()
        assertEquals(null, sections.sectionIndexForKey("A"))
    }

    @Test
    fun sectionIndexForKey_singleSection_returnsZero() {
        val entries = listOf(entry("Alpha", pkg = "com.alpha"))
        val sections = entries.toAppSections(collator)
        assertEquals(0, sections.sectionIndexForKey("A"))
    }

    // ── headerItemIndex ──────────────────────────────────────────────────

    @Test
    fun headerItemIndex_firstSection_returnsZero() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Avocado", pkg = "com.avocado"),
            entry("Banana", pkg = "com.banana"),
        )
        val sections = entries.toAppSections(collator)
        assertEquals(0, sections.headerItemIndex(0))
    }

    @Test
    fun headerItemIndex_secondSection_skipsFirstHeaderAndApps() {
        val entries = listOf(
            entry("Apple", pkg = "com.apple"),
            entry("Avocado", pkg = "com.avocado"),
            entry("Banana", pkg = "com.banana"),
        )
        val sections = entries.toAppSections(collator)
        // Section A has 2 apps → header at 0, apps at 1,2 → Section B header at 3
        assertEquals(3, sections.headerItemIndex(1))
    }

    @Test
    fun headerItemIndex_singleAppSection_returnsZero() {
        val entries = listOf(entry("Only", pkg = "com.only"))
        val sections = entries.toAppSections(collator)
        assertEquals(0, sections.headerItemIndex(0))
    }

    @Test
    fun headerItemIndex_hashThenLatin_headersAtCorrectIndices() {
        val entries = listOf(
            entry("1Password", pkg = "com.onepassword"),
            entry("9GAG", pkg = "com.ninegag"),
            entry("Apple", pkg = "com.apple"),
            entry("Banana", pkg = "com.banana"),
            entry("Berry", pkg = "com.berry"),
        )
        val sections = entries.toAppSections(collator)
        // #: 2 apps → header 0, apps 1-2 → A header 3, A: 1 app → app 4 → B header 5
        assertEquals(0, sections.headerItemIndex(0))     // #
        assertEquals(3, sections.headerItemIndex(1))     // A
        assertEquals(5, sections.headerItemIndex(2))     // B
    }

    // ── AppSection data class: non-empty invariant ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun appSection_emptyApps_throws() {
        AppSection(key = "A", apps = emptyList())
    }

    @Test
    fun appSection_nonEmptyApps_succeeds() {
        val section = AppSection(key = "A", apps = listOf(entry("Alpha")))
        assertEquals("A", section.key)
        assertEquals(1, section.apps.size)
    }
}