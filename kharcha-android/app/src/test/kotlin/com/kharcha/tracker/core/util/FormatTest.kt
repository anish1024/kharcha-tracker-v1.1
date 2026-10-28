package com.kharcha.tracker.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function tests for the display formatting helpers. These run on
 * the JVM - no Android runtime, no locale surprises (Locale.US is pinned
 * inside [Format.amount]).
 */
class FormatTest {

    @Test
    fun `date parses ISO-8601 prefixes with fractions, Z suffixes and bare dates`() {
        assertEquals("29 Oct 2026", Format.date("2026-10-29T14:22:03.123456+00:00"))
        assertEquals("29 Oct 2026", Format.date("2026-10-29T14:22:03Z"))
        assertEquals("29 Oct 2026", Format.date("2026-10-29T14:22:03"))
        assertEquals("5 Jan 2025", Format.date("2025-01-05"))
        assertEquals("29 Oct 2026", Format.date("2026-10-29"))
    }

    @Test
    fun `date returns unparseable or missing input unchanged`() {
        assertEquals("not-a-date", Format.date("not-a-date"))
        assertEquals("", Format.date(""))
        assertEquals("", Format.date(null))
    }

    @Test
    fun `date rejects out-of-range months`() {
        assertEquals(
            "2026-13-01T00:00:00Z",
            Format.date("2026-13-01T00:00:00Z")
        )
        assertEquals(
            "2026-00-10T00:00:00Z",
            Format.date("2026-00-10T00:00:00Z")
        )
    }

    @Test
    fun `dateTime appends the clock when present and drops it otherwise`() {
        assertEquals(
            "29 Oct 2026, 14:22",
            Format.dateTime("2026-10-29T14:22:03+00:00")
        )
        assertEquals(
            "29 Oct 2026, 09:05",
            Format.dateTime("2026-10-29T09:05:00.123Z")
        )
        assertEquals("29 Oct 2026", Format.dateTime("2026-10-29"))
        assertEquals("nope", Format.dateTime("nope"))
    }

    @Test
    fun `amount formats with symbols, grouping and absolute value`() {
        assertEquals("₹1,234.50", Format.amount(1234.5, "INR"))
        assertEquals("$9.99", Format.amount(9.99, "USD"))
        assertEquals("JPY 500.00", Format.amount(500.0, "JPY"))
        assertEquals("1,234.50", Format.amount(1234.5, null))
        assertEquals("1,234.50", Format.amount(1234.5, ""))
        assertEquals("₹250.00", Format.amount(-250.0, "INR"))
    }

    @Test
    fun `netPosition phrases owed, owes and settled-up`() {
        assertEquals("You owe ₹250.00", Format.netPosition(-250.0, "INR"))
        assertEquals("You are owed ₹1,000.00", Format.netPosition(1000.0, "INR"))
        assertEquals("You're all settled up", Format.netPosition(0.0, "INR"))
        assertEquals("You're all settled up", Format.netPosition(0.001, "INR"))
    }
}
