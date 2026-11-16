package com.mmgpt.shoplistgpt.spreadsheet

/**
 * Hands out increasing IDs starting at [next]. Importers draw item and category IDs from
 * sequences owned by the caller, so they don't depend on any particular screen's counters
 * and tests can predict the IDs they will get.
 */
class IdSequence(next: Long = 0L) {
    var next: Long = next
        private set

    fun take(): Long = next++
}
