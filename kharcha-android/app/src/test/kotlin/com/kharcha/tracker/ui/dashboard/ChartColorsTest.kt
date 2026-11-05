package com.kharcha.tracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartColorsTest {

    @Test
    fun `same label yields same index`() {
        assertEquals(ChartColors.chartColorIdx("Food"), ChartColors.chartColorIdx("food"))
    }

    @Test
    fun `case and whitespace insensitive`() {
        val idx = ChartColors.chartColorIdx("  Grocery  ")
        assertEquals(idx, ChartColors.chartColorIdx("grocery"))
        assertEquals(idx, ChartColors.chartColorIdx("GROCERY"))
    }

    @Test
    fun `palette bounds 0-9`() {
        val idx = ChartColors.chartColorIdx("X")
        assertTrue(idx >= 0)
        assertTrue(idx < 10)
    }
}