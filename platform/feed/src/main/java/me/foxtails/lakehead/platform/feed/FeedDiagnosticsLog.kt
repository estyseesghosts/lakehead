package me.foxtails.lakehead.platform.feed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Framework-free rendering of the gateway's diagnostic streams as single log lines.
 *
 * Only enum names, booleans, provider identities and allow-listed package ids are
 * written — never exception text, intents, binders or URIs — so a line is safe to log
 * for the same reason [FeedDiagnostic] is safe to hold in state.
 */
internal fun formatFeedState(state: FeedAvailability): String = when (state) {
    is FeedAvailability.Available -> "state Available provider=${providerName(state.provider)}"
    is FeedAvailability.Unavailable -> "state Unavailable reason=${state.reason}"
}

internal fun formatFeedDiagnostic(diagnostic: FeedDiagnostic?): String {
    if (diagnostic == null) return "diagnostic none"
    return "diagnostic status=${diagnostic.status} stage=${diagnostic.stage} " +
        "cause=${diagnostic.cause} provider=${providerName(diagnostic.provider)} " +
        "package=${diagnostic.packageName ?: "-"}"
}

internal fun formatFeedBinding(evidence: FeedBindingEvidence): String =
    "binding provider=${providerName(evidence.target?.provider)} " +
        "attempted=${evidence.attempted} resolvability=${evidence.resolvability ?: "-"} " +
        "accepted=${evidence.accepted} connected=${evidence.connected} " +
        "active=${evidence.active} epoch=${evidence.epoch}"

internal fun formatFeedTrace(trace: FeedProtocolTrace): String {
    val failure = trace.firstFailure
    val failureText = if (failure == null) "none" else "${failure.status}/${failure.cause}"
    return "trace windowSignalSent=${trace.windowSignalSent} " +
        "sessionConfirmed=${trace.sessionConfirmed} " +
        "contentConfirmed=${trace.contentConfirmed} firstFailure=$failureText"
}

private fun providerName(provider: FeedProviderId?): String = when (provider) {
    null -> "-"
    is FeedProviderId.Lawnfeed -> "Lawnfeed"
    is FeedProviderId.NeoFeed -> "NeoFeed"
}

/**
 * Logs every transition of a gateway's diagnostic streams to [sink], one line each.
 *
 * Collects [FeedOverlayGateway.state], [FeedOverlayGateway.diagnostic],
 * [FeedOverlayGateway.bindingEvidence] and [FeedOverlayGateway.protocolTrace] in
 * [scope]. The streams are `StateFlow`s, so equal consecutive values are not repeated
 * and the current value is logged first, giving a per-provider timeline of stage
 * transitions. Owns no state of its own and never touches the gateway's lifecycle.
 *
 * @return the collecting jobs, so a caller (or test) can cancel them.
 */
internal fun FeedOverlayGateway.logDiagnosticTransitions(
    scope: CoroutineScope,
    sink: (String) -> Unit,
): List<Job> = listOf(
    scope.logEach(state, ::formatFeedState, sink),
    scope.logEach(diagnostic, ::formatFeedDiagnostic, sink),
    scope.logEach(bindingEvidence, ::formatFeedBinding, sink),
    scope.logEach(protocolTrace, ::formatFeedTrace, sink),
)

private fun <T> CoroutineScope.logEach(
    flow: StateFlow<T>,
    format: (T) -> String,
    sink: (String) -> Unit,
): Job = launch {
    flow.collect { value ->
        try {
            sink(format(value))
        } catch (_: Exception) {
            // Diagnostics logging never affects hosting.
        }
    }
}
