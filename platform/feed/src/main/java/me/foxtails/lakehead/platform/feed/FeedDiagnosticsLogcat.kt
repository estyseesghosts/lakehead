package me.foxtails.lakehead.platform.feed

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope

/**
 * System property that enables feed diagnostics logging:
 * `adb shell setprop debug.protolaunch.feed_diag 1` before launching the app, then
 * `adb logcat -s FeedDiag`. Unset (the default) means no collection and no log lines.
 */
const val FEED_DIAGNOSTICS_PROPERTY: String = "debug.protolaunch.feed_diag"

/** Logcat tag used by [startFeedDiagnosticsLogging]. */
const val FEED_DIAGNOSTICS_TAG: String = "FeedDiag"

/**
 * Starts logging [gateway]'s diagnostic transitions to logcat in [scope] when
 * [FEED_DIAGNOSTICS_PROPERTY] is `1` or `true`; otherwise does nothing.
 *
 * The property is read once, at call time. Returns whether logging was started.
 */
fun startFeedDiagnosticsLogging(gateway: FeedOverlayGateway, scope: CoroutineScope): Boolean {
    if (!isFeedDiagnosticsEnabled(readSystemProperty(FEED_DIAGNOSTICS_PROPERTY))) return false
    gateway.logDiagnosticTransitions(scope) { line -> Log.i(FEED_DIAGNOSTICS_TAG, line) }
    return true
}

/** Pure gate decision for a raw property value, so the accepted spellings are pinned by tests. */
internal fun isFeedDiagnosticsEnabled(value: String?): Boolean =
    value == "1" || value.equals("true", ignoreCase = true)

/**
 * Reads a system property via `android.os.SystemProperties`, which is not public SDK.
 * Reflection is the established route for `debug.*` properties; any failure reads as unset.
 */
@SuppressLint("PrivateApi")
private fun readSystemProperty(name: String): String? = try {
    Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java, String::class.java)
        .invoke(null, name, "") as? String
} catch (_: Exception) {
    null
}
