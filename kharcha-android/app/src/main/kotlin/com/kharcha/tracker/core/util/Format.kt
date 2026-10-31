package com.kharcha.tracker.core.util

import java.util.Locale
import kotlin.math.abs

/**
 * Pure display-formatting helpers, unit-testable on the JVM.
 *
 * The gateway sends ISO-8601 datetimes as strings (e.g.
 * `2026-10-29T14:22:03.123456+00:00`, `...Z`, or a bare date). The
 * date helpers below parse the date/time prefix by string manipulation
 * only - safe on minSdk 24 without core library desugaring (no
 * `java.time`). Unparseable input is returned unchanged so the UI can
 * fall back to showing the raw value.
 */
object Format {

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private val CURRENCY_SYMBOLS = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£"
    )

    /**
     * `2026-10-29T...` -> `29 Oct 2026`. Returns the input unchanged when
     * the leading `yyyy-MM-dd` part is missing or out of range.
     */
    fun date(iso8601: String?): String {
        if (iso8601.isNullOrBlank()) return iso8601.orEmpty()
        val parts = iso8601.take(10).split("-")
        if (parts.size != 3) return iso8601
        val (year, month, day) = parts
        val monthIndex = month.toIntOrNull()?.minus(1) ?: return iso8601
        val monthName = MONTHS.getOrNull(monthIndex) ?: return iso8601
        val dayNumber = day.toIntOrNull() ?: return iso8601
        if (dayNumber !in 1..31 || year.toIntOrNull() == null) return iso8601
        return "$dayNumber $monthName $year"
    }

    /**
     * `2026-10-29T14:22:03...` -> `29 Oct 2026, 14:22` (the clock part is
     * dropped when absent, so a bare date renders as [date]).
     */
    fun dateTime(iso8601: String?): String {
        if (iso8601.isNullOrBlank()) return iso8601.orEmpty()
        val dateOnly = date(iso8601)
        if (dateOnly == iso8601) return iso8601 // not a date at all
        val clock = iso8601
            .substringAfter('T', missingDelimiterValue = "")
            .takeWhile { it.isDigit() || it == ':' }
            .split(":")
            .take(2)
            .takeIf { it.size == 2 && it[0].length == 2 && it[1].length == 2 }
            ?.joinToString(":")
            .orEmpty()
        return if (clock.isEmpty()) dateOnly else "$dateOnly, $clock"
    }

    /**
     * Formats an absolute amount with a currency symbol:
     * `1234.5` + `INR` -> `₹1,234.50`; unknown codes are prefixed
     * (`JPY 500.00`); a null/blank currency is bare (`1,234.50`).
     */
    fun amount(value: Double, currency: String?): String {
        val digits = String.format(Locale.US, "%,.2f", abs(value))
        val code = currency?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
        val prefix = when {
            code == null -> ""
            CURRENCY_SYMBOLS.containsKey(code) -> CURRENCY_SYMBOLS.getValue(code)
            else -> "$code "
        }
        return prefix + digits
    }

    /**
     * Phrases a member's net position (see [com.kharcha.tracker.data.remote.dto.SettlementPreviewDto]
     * for the sign convention): positive = they are owed, negative = they
     * owe, ~0 = settled up.
     */
    fun netPosition(net: Double, currency: String?): String = when {
        abs(net) < 0.005 -> "You're all settled up"
        net > 0 -> "You are owed ${amount(net, currency)}"
        else -> "You owe ${amount(net, currency)}"
    }
}
