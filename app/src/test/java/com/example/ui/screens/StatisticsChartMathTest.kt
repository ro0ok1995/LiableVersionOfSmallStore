package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsChartMathTest {
    @Test
    fun `percentages include every category and sum to 100`() {
        val result = calculateChartPercentages(listOf(500.0, 300.0, 100.0, 50.0, 50.0))
        assertEquals(listOf(50, 30, 10, 5, 5), result)
        assertEquals(100, result.sum())
    }

    @Test
    fun `returns receive a nonzero share when present`() {
        val result = calculateChartPercentages(listOf(900.0, 0.0, 0.0, 0.0, 100.0))
        assertEquals(90, result[0])
        assertEquals(10, result[4])
        assertEquals(100, result.sum())
    }

    @Test
    fun `small categories are rounded with largest remainder without losing total`() {
        val result = calculateChartPercentages(listOf(98.0, 1.0, 1.0))
        assertEquals(100, result.sum())
        assertTrue(result[1] >= 1)
        assertTrue(result[2] >= 1)
    }

    @Test
    fun `empty or invalid data produces zeros instead of NaN`() {
        assertEquals(listOf(0, 0), calculateChartPercentages(listOf(0.0, 0.0)))
        assertEquals(listOf(0, 0), calculateChartPercentages(listOf(Double.NaN, -10.0)))
    }
}
