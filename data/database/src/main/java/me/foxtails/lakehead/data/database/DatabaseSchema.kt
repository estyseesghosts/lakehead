package me.foxtails.lakehead.data.database

/** Schema evolution policy: only explicit, data-preserving migrations may be registered. */
object DatabaseSchema {
    const val VERSION = 3
}
