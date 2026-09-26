package me.foxtails.lakehead.feature.allapps

/**
 * Transient picker state for the P4 alphabet jump overlay.
 *
 * Pure value holder: [isOpen] controls visibility and [enabledLetters] carries the
 * P3 [AppSection.key] set that may be jumped to. Keys stay [String] (not [Char]) to
 * match P3 grouping, including `"#"` and non-Latin single-char keys. No persistence,
 * no Android/Compose dependency; callers own open/close transitions and section jumps.
 */
data class AlphabetPickerState(
    val isOpen: Boolean,
    val enabledLetters: Set<String> = emptySet(),
)

/**
 * Derives picker state from grouped [AppSection]s.
 *
 * Pure: collects each section's [AppSection.key] into [AlphabetPickerState.enabledLetters]
 * with [isOpen] as supplied. Empty input yields empty enablement (picker unopenable
 * per P3 empty→no-headers-picker-unopenable).
 */
fun List<AppSection>.toAlphabetPickerState(isOpen: Boolean): AlphabetPickerState =
    AlphabetPickerState(
        isOpen = isOpen,
        enabledLetters = map { it.key }.toSet(),
    )

/**
 * Adapts [AlphabetPickerState.enabledLetters] ([String] keys) to the [Char] set the
 * `AlphabetPickerGrid` consumes: single-character keys contribute their char, longer
 * keys never occur from P3 but are ignored rather than crashing.
 */
fun AlphabetPickerState.enabledChars(): Set<Char> =
    enabledLetters.mapNotNull { key -> key.singleOrNull() }.toSet()

/**
 * Pager-block flag: horizontal launcher paging is disabled while the picker is open.
 *
 * Null (no picker supplied) never blocks.
 */
fun AlphabetPickerState?.blocksPager(): Boolean = this?.isOpen == true
