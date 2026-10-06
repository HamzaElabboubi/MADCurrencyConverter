package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.HistoryEntry

/** Pure rules for the conversion history list (newest first). */
object ConversionHistory {

    const val MAX_ENTRIES = 50

    /** Copy/Share of the same conversion within this window counts once. */
    const val DEDUP_WINDOW_MILLIS = 10 * 60 * 1000L

    /**
     * Adds [entry] at the top of [entries], keeping at most [maxEntries] (oldest dropped).
     *
     * Deduplication: [entry] is ignored when the most recent entry has the same currencies,
     * amount, converted amount and rate (numerically equal) and was recorded less than
     * [dedupWindowMillis] before it. Only the most recent entry is compared, so A, B, A
     * records three entries, while copying then sharing the same result records one.
     */
    fun add(
        entries: List<HistoryEntry>,
        entry: HistoryEntry,
        maxEntries: Int = MAX_ENTRIES,
        dedupWindowMillis: Long = DEDUP_WINDOW_MILLIS,
    ): List<HistoryEntry> {
        val latest = entries.firstOrNull()
        if (latest != null && latest.isSameConversion(entry)) {
            val elapsed = entry.timestampMillis - latest.timestampMillis
            if (elapsed in 0 until dedupWindowMillis) return entries
        }
        return (listOf(entry) + entries).take(maxEntries)
    }

    private fun HistoryEntry.isSameConversion(other: HistoryEntry): Boolean =
        from == other.from &&
            to == other.to &&
            amount.compareTo(other.amount) == 0 &&
            convertedAmount.compareTo(other.convertedAmount) == 0 &&
            rate.compareTo(other.rate) == 0
}
