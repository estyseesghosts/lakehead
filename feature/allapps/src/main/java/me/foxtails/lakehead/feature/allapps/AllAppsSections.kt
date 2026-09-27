package me.foxtails.lakehead.feature.allapps

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * A named section of [AllAppsEntry] items sharing the same [key].
 *
 * [apps] is guaranteed non-empty; callers must not create an [AppSection] with an empty list.
 */
data class AppSection(
    val key: String,
    val apps: List<AllAppsEntry>,
) {
    init {
        require(apps.isNotEmpty()) { "AppSection.apps must be non-empty" }
    }
}

/**
 * Root collator at [java.text.Collator.PRIMARY] strength.
 *
 * Treats base letters and their accented variants as equal
 * (e.g. `é` ≡ `e`, `Ä` ≡ `A`) while ignoring case and diacritics.
 */
fun sectionCollator(): Collator =
    Collator.getInstance(Locale.ROOT).apply {
        strength = Collator.PRIMARY
    }

/**
 * Derives a single-character section key from an app sort key.
 *
 * Rules (in priority order):
 * 1. Blank sort key → `"#"`
 * 2. First char is `#` → `"#"`
 * 3. First char folds to A–Z via [collator] (Latin, case/diacritic insensitive) → that uppercase letter
 * 4. First char is a Unicode letter outside A–Z → its uppercase form
 * 5. Anything else (digit, symbol, punctuation) → `"#"`
 */
fun groupKey(sortKey: String, collator: Collator): String {
    val first = sortKey.trimStart().firstOrNull() ?: return "#"
    if (first == '#') return "#"

    // Check Latin A–Z by folding diacritics and case.
    if (first.isAsciiLatinLetter()) {
        return first.uppercaseChar().toString()
    }

    // Decompose to NFD so combining marks (accents, diacritics) are separable,
    // strip them, and re-check: é→e→E, ä→a→A, ñ→n→N, etc.
    val decomposed = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
    val stripped = DIACRITICAL_MARK_REGEX.replace(decomposed, "")
    val base = stripped.firstOrNull() ?: return "#"
    if (base.isAsciiLatinLetter()) {
        return base.uppercaseChar().toString()
    }

    // Non-Latin letter (Cyrillic, Greek, CJK, Hangul, Arabic, …) → uppercase single char.
    if (first.isLetter()) {
        return first.uppercaseChar().toString()
    }

    // Digit, symbol, punctuation, other → catch-all bucket.
    return "#"
}

/**
 * Groups this list into [AppSection]s using the default root [sectionCollator].
 *
 * Convenience for presentation callers that must not own a cached collator; delegates to
 * [toAppSections] with [sectionCollator].
 */
fun List<AllAppsEntry>.toAppSections(): List<AppSection> = toAppSections(sectionCollator())

/**
 * Groups this list into [AppSection]s.
 *
 * - Input order is preserved within each section.
 * - Section order: `"#"` first, then `A`–`Z`, then remaining non-Latin keys
 *   in [collator] order.
 * - Empty sections are omitted.
 */
fun List<AllAppsEntry>.toAppSections(collator: Collator): List<AppSection> {    if (isEmpty()) return emptyList()

    // Stable grouping: LinkedHashMap preserves first-insertion order.
    val grouped = LinkedHashMap<String, MutableList<AllAppsEntry>>()
    for (entry in this) {
        val key = groupKey(entry.sortKey, collator)
        grouped.getOrPut(key) { mutableListOf() }.add(entry)
    }

    // Partition into the fixed Latin order and the collator-sorted tail.
    val latinKeys = LATIN_ORDER.filter { it in grouped }
    val otherKeys = grouped.keys
        .filter { it !in LATIN_ORDER }
        .sortedWith(collator)

    return (latinKeys + otherKeys).map { key ->
        val apps = grouped.getValue(key)
        val orderedApps = apps.sortedWith(
            compareBy<AllAppsEntry, String>(collator) { it.sortKey }
                .thenBy { it.identity.component.packageName }
                .thenBy { it.identity.component.className }
                .thenBy { it.identity.profileId.value },
        )
        AppSection(
            key = key,
            apps = orderedApps,
        )
    }
}

// ── index helpers ────────────────────────────────────────────────────────

/**
 * Returns the index of the [AppSection] whose [AppSection.key] equals [key],
 * or `null` if no section matches or [sections] is empty.
 */
fun List<AppSection>.sectionIndexForKey(key: String): Int? =
    indexOfFirst { it.key == key }.takeIf { it >= 0 }

/**
 * Returns the LazyColumn item index of the header for the section at [sectionIndex].
 *
 * Each section contributes 1 header item followed by [AppSection.apps].size row items.
 * The header of the first section is always at item index 0.
 *
 * @throws IllegalArgumentException if [sectionIndex] is out of range.
 */
fun List<AppSection>.headerItemIndex(sectionIndex: Int): Int {
    require(sectionIndex in indices) { "sectionIndex $sectionIndex out of range $indices" }
    var idx = 0
    for (i in 0 until sectionIndex) {
        idx += 1 + this[i].apps.size
    }
    return idx
}

// ── private helpers ──────────────────────────────────────────────────────

/** Pre-defined section order: `#` then `A`–`Z`. */
private val LATIN_ORDER: List<String> =
    listOf("#") + ('A'..'Z').map { it.toString() }

/** Regex matching Unicode combining diacritical marks (accents, tildes, cedillas, …). */
private val DIACRITICAL_MARK_REGEX: Regex =
    Regex("\\p{InCombiningDiacriticalMarks}+")

/** Fast ASCII-letter check that avoids Character.isLetter ambiguity with non-Latin scripts. */
private fun Char.isAsciiLatinLetter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z'
