# ProtoLaunch release-build consumer ProGuard rules.
#
# Keep this file additive and tied to evidence: every rule below is
# here because a library we ship uses reflection / generated code that
# R8 cannot otherwise trace.  When a library grows its own
# `consumer-rules.pro`, remove the rule here rather than duplicating it.
#
# Documentation-only rules are explicitly labelled as such so the
# future agent does not chase a false positive.  AGENTS.md §5 forbids
# speculative extension points, so prefer deleting a rule over
# commenting it out.

# ─── Room ──────────────────────────────────────────────────────────────────
# Room generates `PlacementDatabase_Impl` plus DAO `_Impl` classes via the
# annotation processor and instantiates them reflectively at runtime.
# Room's own consumer rules cover most of this, but the explicit keeps
# below are belt-and-braces against a regression if Room's bundled
# rules change.
-keep class me.foxtails.lakehead.data.database.PlacementDatabase { *; }
-keep class me.foxtails.lakehead.data.database.PlacementDatabase_Impl { *; }
-keep class me.foxtails.lakehead.data.database.**_Impl { *; }
# Type converters and the entity classes Room reflects into are
# referenced by name from the generated `_Impl`.
-keep @androidx.room.TypeConverter class * { *; }
-keep class me.foxtails.lakehead.data.database.*Entity { *; }
-keep class me.foxtails.lakehead.data.database.*Dao { *; }

# ─── kotlinx.coroutines ────────────────────────────────────────────────────
# `Dispatchers.Main` resolves through the `MainDispatcherFactory` SPI.
# R8 strips the implementation class without this rule, and the app
# crashes the first time a coroutine hits `Dispatchers.Main`.
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherLoader
-dontwarn kotlinx.coroutines.debug.AgentPremain

# ─── @Keep ─────────────────────────────────────────────────────────────────
# Honour the @androidx.annotation.Keep annotation everywhere.
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ─── Compose ───────────────────────────────────────────────────────────────
# Compose Compiler already wires the runtime + compiler contracts;
# these are documentation-only to make future audits easier.  Removing
# them does not change the output.
#
# -keep class androidx.compose.runtime.** { *; }   ; intentionally not added
#
# The Compose companion toolchain emits the rules it needs at packaging
# time.  Adding our own here would duplicate them.

# ─── Instrumentation test classes ──────────────────────────────────────────
# `*Test` classes are not on the dexed `release` classpath, so R8 will
# not see them.  No rule required; documented so future agents do not
# chase a missing-class warning that turns out to be a lint tripwire.

# ─── Misc ─────────────────────────────────────────────────────────────────
# Silence R8 warnings about classes pulled in transitively but never
# invoked from this app.  Each line corresponds to a `dontwarn` only;
# no code is preserved.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**