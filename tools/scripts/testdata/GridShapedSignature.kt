// Regression fixture for tools/scripts/function_audit.py.
// This file is never compiled: it pins the parser's signature/braces handling.
//
// Braces in this line comment ({ and }) must not be counted.
package fixture

private val braceInString = "{"

private val rawStringBrace = """
    { unbalanced brace inside a raw string
"""

private val quoteInChar = '{'

/**
 * KDoc mentioning a brace { and a lambda default { _, _ -> } in prose.
 */
internal fun lambdaDefaultsInSignature(
    // A default argument whose lambda balances on this very line. Counting
    // braces from the `fun` keyword without skipping the signature stops here
    // and reports a signature-sized function.
    onMove: (Int, Long) -> Unit = { _, _ -> },
    onResize: (Int) -> Unit = {},
    label: String = "{",
): Int {
    val seed = onMove.hashCode() + onResize.hashCode()
    return if (label.isEmpty()) seed else seed + 1
}

internal fun multiLineParameterList(
    first: Int,
    second: String = "two}",
): String = second.repeat(first)

internal fun nestedBlocks() {
    fun inner() {
        val text = "}"
        println(text)
    }
    inner()
}

fun interface Callback {
    fun invoke(value: Int)
}

internal fun anonymousLambda(val block: () -> Unit) = block()

internal fun lambdaContainingNamedFunction() {
    val runner = fun(): Int {
        return 7
    }
    println(runner())
}

/**
 * Shaped like HomeCanvasGrid(): a long parameter list with several lambda
 * defaults, interleaved comments and string defaults, followed by a body long
 * enough to cross the audit threshold. The signature must be skipped in full.
 */
internal fun gridShapedSignature(
    loaded: Int,
    iconsByIdentity: Int,
    onLaunchApp: (Int) -> Unit,
    overscanRows: Long,
    viewportStyle: Int,
    usableHeightPx: Int,
    fullHeightPx: Int = usableHeightPx,
    onMoveTile: (Int, Int, Long) -> Unit = { _, _, _ -> },
    onResizeTile: (Int, Int) -> Unit = { _, _ -> },
    onRemoveTile: (Int) -> Unit = {},
    surfaceEntries: List<String> = emptyList(),
    surfaceEntriesTileId: Int? = null,
    onSelectedTileChange: (Int?) -> Unit = {},
    onSurfaceExecute: (Int, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (Int, String) -> Unit = { _, _ -> },
    pinnedByTile: Map<Int, List<String>> = emptyMap(),
    onPinnedShortcutClick: (Int, String) -> Unit = { _, _ -> },
    reducedMotion: Boolean = false,
    isVisible: Boolean = true,
): Int {
    var accumulator = 0
    accumulator += loaded
    accumulator += iconsByIdentity
    accumulator += overscanRows.toInt()
    accumulator += viewportStyle
    accumulator += usableHeightPx
    accumulator += fullHeightPx
    accumulator += surfaceEntries.size
    accumulator += (surfaceEntriesTileId ?: 0)
    accumulator += pinnedByTile.size
    if (reducedMotion) {
        accumulator += 1
    }
    if (!isVisible) {
        accumulator -= 1
    }
    onLaunchApp(loaded)
    onMoveTile(loaded, 0, 0L)
    onResizeTile(loaded, 1)
    onRemoveTile(loaded)
    onSelectedTileChange(loaded)
    onSurfaceExecute(loaded, "execute")
    onSurfaceTogglePin(loaded, "toggle")
    onPinnedShortcutClick(loaded, "shortcut")
    return when {
        accumulator > 100 -> accumulator
        accumulator > 50 -> accumulator * 2
        accumulator > 0 -> accumulator + 1
        else -> 0
    }
}

internal fun <T> genericWithWhereClause(item: T): T where T : Comparable<T> {
    val ordered = listOf(item).sorted()
    return ordered.first()
}

internal fun <K, V> genericOverNestedTypes(rows: Map<K, List<V>>): Int =
    rows.size

internal fun braceInsideBodyText(): String {
    val sample = "} not a closing brace {"
    val raw = """
        }
    """
    return sample + raw + quoteInChar.toString() + braceInString
}