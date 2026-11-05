package com.kharcha.tracker.ui.dashboard

import android.graphics.Color
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.ExpenseDto

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Stage 8c (E4): pure chart data layer.
 *
 * No Compose / Canvas dependencies — unit-testable on the JVM.
 * MinSdk 24 safe: uses java.util.Calendar (no java.time, no desugaring).
 */

object ChartColors {
    private val PALETTE = arrayOf(
        "#4f46e5", "#0ea5e9", "#14b8a6", "#f59e0b",
        "#f43f5e", "#8b5cf6", "#84cc16", "#ec4899",
        "#64748b", "#0891b2"
    )

    /** Pure hash → palette index, testable on JVM without Android APIs. */
    private fun hashIdx(normalized: String): Int {
        var h = 0
        for (c in normalized) {
            h = h * 31 + c.code
        }
        return Math.abs(h % PALETTE.size)
    }

    /** Android UI: parseColor requires roblectric on JVM. Use chartColorIdx + Color.parseColor. */
    fun colorFor(label: String): Int {
        val normalized = label.trim().lowercase()
        val idx = hashIdx(normalized)
        return Color.parseColor(PALETTE[idx])
    }

    /** Pure hash → palette index, testable on JVM without Android APIs.
     * Normalizes by trimming and lowercasing (same as colorFor). */
    fun chartColorIdx(label: String): Int {
        val normalized = label.trim().lowercase()
        var h = 0
        for (c in normalized) {
            h = h * 31 + c.code
        }
        return Math.abs(h % PALETTE.size)
    }

    /** Convert palette index to actual Android Color. */
    fun toColor(idx: Int): Int {
        return Color.parseColor(PALETTE[idx])
    }
}

enum class ChartRange { DAILY, WEEKLY, MONTHLY }

data class StackedBucket(
    val label: String,
    val total: Double,
    val valueText: String,
    val parts: List<ChartPart>
)

data class ChartPart(
    val label: String,
    val value: Double,
    val color: Int
)

data class CategorySlice(
    val label: String,
    val value: Double,
    val color: Int,
    val percentage: Double
)

fun buildRangeSeries(
    expenses: List<ExpenseDto>,
    range: ChartRange,
    currency: String,
    now: Long
): List<StackedBucket> {
    val buckets = mutableMapOf<String, MutableMap<String, Double>>()
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))

    for (e in expenses) {
        val cat = e.category?.trim()?.takeIf { it.isNotBlank() } ?: "Other"
        val dt = e.dateTime ?: continue
        val epoch = parseEpochMillis(dt) ?: continue
        val key = bucketKey(range, epoch, now)
        val inner = buckets.getOrPut(key) { mutableMapOf() }
        inner[cat] = inner.getOrDefault(cat, 0.0) + e.amount
    }

    val orderedKeys = bucketKeysInOrder(range, now)
    val result = mutableListOf<StackedBucket>()
    for (key in orderedKeys) {
        val catMap = buckets[key] ?: continue
        val parts = catMap.entries
            .map { (cat, value) -> ChartPart(cat, value, ChartColors.chartColorIdx(cat)) }
            .sortedByDescending { it.value }
        val total = parts.sumOf { it.value }
        result.add(StackedBucket(key, total, Format.amount(total, currency), parts))
    }
    return result
}

fun buildCategorySlices(
    expenses: List<ExpenseDto>,
    currency: String
): List<CategorySlice> {
    val totals = mutableMapOf<String, Double>()
    for (e in expenses) {
        val cat = e.category?.trim()?.takeIf { it.isNotBlank() } ?: "Other"
        totals[cat] = totals.getOrDefault(cat, 0.0) + e.amount
    }
    val grand = totals.values.sum()
    return totals.entries
        .map { (cat, value) ->
            CategorySlice(
                label = cat,
                value = value,
                color = ChartColors.chartColorIdx(cat),
                percentage = if (grand > 0) value / grand else 0.0
            )
        }
        .sortedByDescending { it.value }
}

private fun parseEpochMillis(iso: String): Long? {
    return try {
        val parts = iso.take(10).split("-")
        if (parts.size != 3) return null
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val timePart = iso.substringAfter('T', missingDelimiterValue = "").takeWhile { it.isDigit() || it == ':' }
        val timeSplit = timePart.split(":")
        val hour = timeSplit.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = timeSplit.getOrNull(1)?.toIntOrNull() ?: 0
        val second = timeSplit.getOrNull(2)?.toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.timeInMillis
    } catch (_: Exception) {
        null
    }
}

private fun bucketKey(range: ChartRange, epoch: Long, now: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = epoch
    return when (range) {
        ChartRange.DAILY -> {
            val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val m = cal.get(java.util.Calendar.MONTH)
            "${MONTHS[m]} $d"
        }
        ChartRange.WEEKLY -> {
            val w = cal.get(java.util.Calendar.WEEK_OF_YEAR)
            "W$w"
        }
        ChartRange.MONTHLY -> {
            val m = cal.get(java.util.Calendar.MONTH)
            val y = cal.get(java.util.Calendar.YEAR)
            "${MONTHS[m]} $y"
        }
    }
}

private fun bucketKeysInOrder(range: ChartRange, now: Long): List<String> {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = now
    val keys = mutableListOf<String>()
    when (range) {
        ChartRange.DAILY -> {
            for (i in 13 downTo 0) {
                val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                c.timeInMillis = now - i * 24 * 60 * 60 * 1000L
                val d = c.get(java.util.Calendar.DAY_OF_MONTH)
                val m = c.get(java.util.Calendar.MONTH)
                keys.add("${MONTHS[m]} $d")
            }
        }
        ChartRange.WEEKLY -> {
            for (i in 7 downTo 0) {
                val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                c.timeInMillis = now - i * 7 * 24 * 60 * 60 * 1000L
                val w = c.get(java.util.Calendar.WEEK_OF_YEAR)
                keys.add("W$w")
            }
        }
        ChartRange.MONTHLY -> {
            for (i in 5 downTo 0) {
                val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                c.add(java.util.Calendar.MONTH, -i)
                val m = c.get(java.util.Calendar.MONTH)
                val y = c.get(java.util.Calendar.YEAR)
                keys.add("${MONTHS[m]} $y")
            }
        }
    }
    return keys
}