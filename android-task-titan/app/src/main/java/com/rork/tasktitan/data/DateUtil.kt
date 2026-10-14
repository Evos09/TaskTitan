package com.rork.tasktitan.data

import java.util.Calendar
import java.util.TimeZone

/** Epoch-day helpers that work on API 24 without java.time desugaring. */
object DateUtil {
    private const val DAY_MS: Long = 86_400_000L
    private val shortDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / DAY_MS
    }

    private fun utc(day: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * DAY_MS }

    fun dayOfMonth(day: Long): Int = utc(day).get(Calendar.DAY_OF_MONTH)

    fun lengthOfMonth(day: Long): Int = utc(day).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** 0 = Sunday … 6 = Saturday */
    fun dayOfWeek(day: Long): Int = utc(day).get(Calendar.DAY_OF_WEEK) - 1

    fun month(day: Long): Int = utc(day).get(Calendar.MONTH)

    fun shortDay(day: Long): String = shortDays[dayOfWeek(day)]

    fun label(day: Long): String = "${months[month(day)]} ${dayOfMonth(day)}"

    fun weekStart(day: Long): Long = day - dayOfWeek(day)

    fun hourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
