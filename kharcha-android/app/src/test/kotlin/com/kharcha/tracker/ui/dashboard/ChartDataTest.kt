package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartDataTest {

    private val now = 1735689600000L // 2025-01-01 00:00 UTC

    @Test
    fun `buildRangeSeries produces bucket labels and counts`() {
        val expenses = listOf(
            ExpenseDto(transId = 1, dateTime = "2024-12-31T10:00:00", category = "Food", description = "x", amount = 100.0),
            ExpenseDto(transId = 2, dateTime = "2025-01-01T11:00:00", category = "Food", description = "x", amount = 50.0)
        )
        val result = buildRangeSeries(expenses, ChartRange.DAILY, "INR", now)
        val nonEmpty = result.filter { it.total > 0 }
        assertEquals(2, nonEmpty.size) // 2024-12-31 and 2025-01-01 are within the 14-day window
        val totalFood = result.sumOf { it.total }
        assertEquals(150.0, totalFood, 0.01)
    }

    @Test
    fun `category parts descend by value`() {
        val expenses = listOf(
            ExpenseDto(transId = 1, dateTime = "2025-01-01T10:00:00", category = "A", description = "x", amount = 200.0)
        )
        val result = buildRangeSeries(expenses, ChartRange.DAILY, "INR", now)
        val bucket = result.first { it.total > 0 }
        assertEquals(1, bucket.parts.size)
        assertEquals("A", bucket.parts[0].label)
    }

    @Test
    fun `buildCategorySlices sums and orders descending`() {
        val expenses = listOf(
            ExpenseDto(transId = 1, dateTime = "2025-01-01T10:00:00", category = "B", description = "x", amount = 30.0),
            ExpenseDto(transId = 2, dateTime = "2025-01-01T10:00:00", category = "A", description = "x", amount = 70.0)
        )
        val slices = buildCategorySlices(expenses, "INR")
        assertEquals(2, slices.size)
        assertEquals("A", slices[0].label)
        assertEquals(70.0, slices[0].value, 0.01)
    }

    @Test
    fun `ChartColors same label same colour index`() {
        val i1 = ChartColors.chartColorIdx("Groceries")
        val i2 = ChartColors.chartColorIdx("groceries")
        assertEquals(i1, i2)
    }

    @Test
    fun `ChartColors whitespace insensitive`() {
        val i1 = ChartColors.chartColorIdx("Groceries")
        val i3 = ChartColors.chartColorIdx("  Groceries  ")
        assertEquals(i1, i3)
    }
}