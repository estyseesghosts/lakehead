package me.foxtails.lakehead.core.grid

/** The positive number of logical columns in a grid. */
data class GridColumns(val count: Int) {
    init {
        require(count > 0) { "count must be positive" }
    }

    companion object {
        /** Normal-phone reference count; this is not a minimum. */
        const val NORMAL_PHONE_REFERENCE_COUNT: Int = 6
    }
}
