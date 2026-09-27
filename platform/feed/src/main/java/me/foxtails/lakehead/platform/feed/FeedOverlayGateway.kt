package me.foxtails.lakehead.platform.feed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle owner for one Launcher3-style feed overlay connection.
 *
 * The gateway owns availability state and the single bound overlay service. It holds no
 * framework types beyond [StateFlow] and [CoroutineScope], so composables and domain code can
 * consume [state] without touching `Context`, `ServiceConnection` or `IBinder`. All Android
 * work lives in the implementation behind this seam.
 *
 * Every method is idempotent: calling [start] twice keeps a single resolution stream, calling
 * [stop] without [start] is a no-op, attaching the already-attached provider keeps the single
 * connection, and [detach] without a connection is a no-op.
 */
interface FeedOverlayGateway {

    /**
     * Current hosting verdict, starting honest.
     *
     * The initial value is [FeedAvailability.Unavailable] with
     * [FeedAvailability.Unavailable.Reason.NO_PROVIDER]: nothing has been proven until the first
     * off-main resolution after [start] runs. Later values come from resolution, binds and
     * connection callbacks, never from placeholder data.
     */
    val state: StateFlow<FeedAvailability>

    /**
     * Begins off-main resolution and package-change invalidation in [scope].
     *
     * Idempotent: a second call while started keeps the existing stream and registers nothing
     * new. The caller owns [scope]; [stop] never cancels it.
     */
    fun start(scope: CoroutineScope)

    /**
     * Detaches the overlay, stops package-change invalidation and drops the scope.
     *
     * Idempotent: safe to call without [start] or twice in a row.
     */
    fun stop()

    /**
     * Binds the overlay service published by [provider].
     *
     * Keeps a single connection: attaching the already-attached provider reuses it, attaching a
     * different provider unbinds the old one first. Never throws for bind failures; they surface
     * as [FeedAvailability.Unavailable].
     */
    fun attach(provider: FeedProviderId)

    /** Unbinds the overlay service if one is bound, otherwise a no-op. Never throws. */
    fun detach()

    /**
     * Forwards a host visibility change toward the bound overlay.
     *
     * Caches [visible] first so the value survives unbinds and is re-delivered on the next
     * connect (catch-up); delivery to the bound control channel is best-effort. Never throws,
     * including when unbound.
     */
    fun notifyOverlayVisible(visible: Boolean)

    /**
     * Forwards a host scroll progress change (0..1) toward the bound overlay.
     *
     * Same cache-then-deliver contract as [notifyOverlayVisible]; out-of-range values are
     * clamped and `NaN` maps to `0` (see [coerceOverlayProgress]). Never throws, including
     * when unbound.
     */
    fun notifyOverlayProgress(progress: Float)
}
