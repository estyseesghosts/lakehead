package me.foxtails.lakehead.platform.feed

/**
 * D4 framework-free trace of one overlay connection along
 * binder → negotiation → window/session → render.
 *
 * Pure and immutable: the [AidlFeedBridge] publishes snapshots of this shape while the
 * gateway (or device tooling) reads them without touching `IBinder` or AIDL stubs.
 * Every flag is monotonic — once confirmed it never un-confirms — and [firstFailure]
 * is set-once, so concurrent observers always agree on the earliest verdict.
 *
 * Honesty rules, mirroring the Launcher3 overlay protocol:
 * - `windowAttached` is a `oneway` transact with no acknowledgement: [windowSignalSent]
 *   means the signal left the host, never that the provider holds a session.
 * - [sessionConfirmed] requires a provider callback (`overlayScrollChanged` or
 *   `overlayStatusChanged`) actually observed on this connection's callback identity.
 * - [contentConfirmed] requires an explicit provider content signal
 *   (`hasOverlayContent() == true` polled off-main via
 *   [AidlFeedBridge.queryHasContent]). The host surface tag is never render proof.
 * - Anything not yet observed stays `false`: unconfirmed, never invented.
 */
data class FeedProtocolTrace(
    /** A `windowAttached` transact completed without a transport error. Signal sent, not acked. */
    val windowSignalSent: Boolean = false,
    /** A provider callback was observed on this connection's callback identity. */
    val sessionConfirmed: Boolean = false,
    /** The provider explicitly reported it holds overlay content. */
    val contentConfirmed: Boolean = false,
    /** The first protocol-stage failure observed, null while every stage is unfailed. */
    val firstFailure: FeedDiagnostic? = null,
) {
    /** Sets [windowSignalSent]; idempotent, never clears. */
    fun withWindowSignalSent(): FeedProtocolTrace =
        if (windowSignalSent) this else copy(windowSignalSent = true)

    /** Sets [sessionConfirmed]; idempotent, never clears. */
    fun withSessionConfirmed(): FeedProtocolTrace =
        if (sessionConfirmed) this else copy(sessionConfirmed = true)

    /** Sets [contentConfirmed]; idempotent, never clears. */
    fun withContentConfirmed(): FeedProtocolTrace =
        if (contentConfirmed) this else copy(contentConfirmed = true)

    /**
     * Records the first protocol-stage failure; later failures are dropped.
     *
     * Set-once on purpose: the earliest verdict along the path is the diagnosis, and a
     * transport collapse behind it must not overwrite which stage failed first.
     */
    fun withFirstFailure(failure: FeedDiagnostic): FeedProtocolTrace =
        if (firstFailure != null) this else copy(firstFailure = failure)
}
