package com.rork.tasktitan.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilTest {
    @Test
    fun `day of week is zero-based from Sunday`() {
        assertEquals(0, DateUtil.dayOfWeek(day(2025, 1, 5)))
        assertEquals(3, DateUtil.dayOfWeek(day(2025, 1, 8)))
        assertEquals(6, DateUtil.dayOfWeek(day(2025, 1, 11)))
        assertEquals("Wed", DateUtil.shortDay(day(2025, 1, 8)))
    }

    @Test
    fun `week starts on the Sunday on or before the day`() {
        assertEquals(day(2025, 1, 5), DateUtil.weekStart(day(2025, 1, 5)))
        assertEquals(day(2025, 1, 5), DateUtil.weekStart(day(2025, 1, 11)))
        assertEquals(day(2024, 12, 29), DateUtil.weekStart(day(2025, 1, 1)))
    }

    @Test
    fun `month helpers handle leap years`() {
        assertEquals(29, DateUtil.lengthOfMonth(day(2024, 2, 10)))
        assertEquals(28, DateUtil.lengthOfMonth(day(2025, 2, 10)))
        assertEquals(31, DateUtil.lengthOfMonth(day(2025, 12, 1)))
        assertEquals(29, DateUtil.dayOfMonth(day(2024, 2, 29)))
        assertEquals(1, DateUtil.month(day(2024, 2, 29)))
    }

    @Test
    fun `label is short month and day`() {
        assertEquals("Mar 9", DateUtil.label(day(2025, 3, 9)))
        assertEquals("Dec 31", DateUtil.label(day(2025, 12, 31)))
    }
}
