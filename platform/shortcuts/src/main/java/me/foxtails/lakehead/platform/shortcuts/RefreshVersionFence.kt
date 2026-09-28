package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ProfileId

/**
 * Per-identity refresh version fence for shortcut queries.
 *
 * Each query stamps [next] before running; the post-query cache/publish
 * commit must go through [tryCommit] so version validation and the commit
 * run atomically under one lock. A stale query that finishes after a newer
 * stamp fails instead of overwriting and publishing a stale snapshot.
 *
 * The fence is bounded like the snapshot cache: [next] evicts the eldest
 * idle identity (no outstanding stamp) when inserting beyond [maxEntries].
 * Identities with outstanding stamps are never evicted, so a cold in-flight
 * query keeps itsTracked version across eviction pressure and its stale
 * commit still fails after removal/stop invalidation. State for identities
 * that are neither cached nor in-flight is retired via [retire] or [trim];
 * the gateway owns the cached set and calls those after commits, removals
 * and stop. Retiring is fail-closed: a stale stamp whose entry was retired
 * still fails its commit (`null != version`), while a fresh stamp after the
 * retire starts a new version that can commit.
 */
internal class RefreshVersionFence(private val maxEntries: Int = 64) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val lock = Any()

    // Access-ordered so the eldest entry is the eviction victim.
    private val versions = LinkedHashMap<AppIdentity, Long>(16, 0.75f, true)
    private val outstanding = mutableMapOf<AppIdentity, MutableSet<Long>>()

    fun next(identity: AppIdentity): Long = synchronized(lock) {
        if (!versions.containsKey(identity) && versions.size >= maxEntries) {
            val victim = versions.keys.firstOrNull { (outstanding[it]?.size ?: 0) == 0 }
            if (victim != null) {
                versions.remove(victim)
                outstanding.remove(victim)
            }
            // When every tracked identity is in-flight, allow temporary
            // growth rather than evicting a version a running query needs.
        }
        val stamped = (versions[identity] ?: 0L) + 1L
        versions[identity] = stamped
        outstanding.getOrPut(identity) { mutableSetOf() }.add(stamped)
        stamped
    }

    /**
     * Advances [identity]'s version without starting a query, so an
     * in-flight query holding an older stamp fails its [tryCommit] instead
     * of overwriting newer state (package removal and gateway stop). No-op
     * when [identity] was never tracked and has no outstanding stamp, so
     * invalidations alone never grow the fence.
     */
    fun invalidate(identity: AppIdentity) = synchronized(lock) {
        if (!versions.containsKey(identity) && (outstanding[identity]?.size ?: 0) == 0) return@synchronized
        versions[identity] = (versions[identity] ?: 0L) + 1L
    }

    /**
     * Advances every tracked version for [packageName] in [profileId], so
     * in-flight queries for that package/profile fail even when they have
     * no cached entry yet (cold in-flight) and can never restore the
     * removed snapshot.
     */
    fun invalidatePackage(packageName: String, profileId: ProfileId) =
        synchronized(lock) {
            versions.keys
                .filter { it.component.packageName == packageName && it.profileId == profileId }
                .forEach { versions[it] = (versions[it] ?: 0L) + 1L }
        }

    /**
     * Advances every tracked version, so queries in flight across a gateway
     * stop fail their [tryCommit] even when the blocking platform call
     * ignores cancellation and returns afterwards.
     */
    fun invalidateAll() = synchronized(lock) {
        versions.keys.toList().forEach { versions[it] = (versions[it] ?: 0L) + 1L }
    }

    /**
     * Runs [commit] only when [version] is still current and [isCancelled]
     * is false, atomically under the fence lock. Consumes one outstanding
     * stamp for [identity] either way, so a finished query never leaks
     * in-flight state. Returns true when [commit] ran.
     */
    fun tryCommit(
        identity: AppIdentity,
        version: Long,
        isCancelled: () -> Boolean = { false },
        commit: () -> Unit,
    ): Boolean = synchronized(lock) {
        consume(identity, version)
        if (isCancelled()) return@synchronized false
        if (versions[identity] != version) return@synchronized false
        commit()
        true
    }

    /**
     * Releases one outstanding stamp without committing, for queries that
     * return no rows or observe cancellation before the commit. Idempotent
     * per version: releasing an already-consumed stamp is a no-op, so a
     * gateway completion handler and the query body can both release safely.
     */
    fun abandon(identity: AppIdentity, version: Long) = synchronized(lock) {
        consume(identity, version)
    }

    /**
     * Drops fence state for [identity] when it is neither cached nor
     * in-flight. In-flight identities are always retained so a cold query
     * keeps its invalidation tombstone until it finishes.
     */
    fun retire(identity: AppIdentity, isCached: Boolean) = synchronized(lock) {
        if (isCached) return@synchronized
        if ((outstanding[identity]?.size ?: 0) != 0) return@synchronized
        versions.remove(identity)
        outstanding.remove(identity)
    }

    /**
     * Retires every idle identity not in [cached]. In-flight identities are
     * retained regardless of cache membership. Used after commits, removals
     * and stop to keep the fence bounded to cached plus in-flight state.
     */
    fun trim(cached: Set<AppIdentity>) = synchronized(lock) {
        versions.keys.toList().forEach { id ->
            if (id !in cached && (outstanding[id]?.size ?: 0) == 0) {
                versions.remove(id)
                outstanding.remove(id)
            }
        }
    }

    internal fun size(): Int = synchronized(lock) { versions.size }

    internal fun outstandingCount(identity: AppIdentity): Int =
        synchronized(lock) { outstanding[identity]?.size ?: 0 }

    private fun consume(identity: AppIdentity, version: Long) {
        val set = outstanding[identity] ?: return
        set.remove(version)
        if (set.isEmpty()) outstanding.remove(identity)
    }
}
