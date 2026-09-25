package me.foxtails.lakehead.core.designsystem

/**
 * Persisted semantic color role for a home tile.
 *
 * See `docs/grid.md` sections 12-13: tiles resolve this role to Material dynamic
 * colors during composition (Normal to `surfaceContainer`/`onSurface`, Accent to
 * `primaryContainer`/`onPrimaryContainer` or tuned equivalents). Persist the role,
 * never a resolved RGB value.
 */
enum class TileColorRole {
    Normal,
    Accent,
}
