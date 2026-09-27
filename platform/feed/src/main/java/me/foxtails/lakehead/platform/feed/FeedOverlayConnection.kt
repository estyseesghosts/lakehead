package me.foxtails.lakehead.platform.feed

/**
 * Framework-free control channel for one bound feed overlay service.
 *
 * The gateway owns availability and the single `ServiceConnection`; this seam owns only the
 * per-connection control verbs so composables, domain code and JVM tests can talk about the
 * overlay without touching `Context`, `IBinder` or AIDL stubs. There are deliberately no
 * `android.*` imports in this file.
 *
 * Every method is idempotent and never throws: repeating a call with the same value is a
 * no-op, calling after [close] is a silent no-op, and an unusable transport degrades to the
 * gateway's honest `Unavailable` verdict instead of an exception.
 */
interface FeedOverlayConnection {

    /**
     * Requests the overlay page be shown ([visible] true) or hidden (false).
     *
     * Redundant calls with the last value are no-ops; never throws, including when unbound.
     */
    fun setVisible(visible: Boolean)

    /**
     * Requests a host scroll progress in 0..1 toward the overlay.
     *
     * Out-of-range values are clamped and `NaN` maps to `0` (see [coerceOverlayProgress]);
     * never throws, including when unbound.
     */
    fun setProgress(progress: Float)

    /**
     * Releases the transport (death recipient, cached proxy). Terminal and idempotent:
     * the first call tears down, later calls are no-ops. Never throws.
     */
    fun close()
}

/**
 * Clamps a host scroll progress to the 0..1 overlay range, mapping `NaN` to `0`.
 *
 * Pure and framework-free so JVM tests pin the contract: infinities clamp to the nearest
 * bound, `NaN` (unordered, so [Float.coerceIn] alone would pass it through) becomes `0`.
 */
internal fun coerceOverlayProgress(value: Float): Float =
    if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
