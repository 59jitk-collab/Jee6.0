package com.example.util

import com.example.data.local.entities.DailyScheduleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    private fun getUtcCalendar(): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
    }

    private fun getFormatter(): SimpleDateFormat {
        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf
    }

    fun todayDateString(): String {
        return getFormatter().format(Date())
    }

    fun isSunday(dateStr: String): Boolean {
        return try {
            val date = getFormatter().parse(dateStr) ?: return false
            val cal = getUtcCalendar()
            cal.time = date
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (_: Exception) {
            false
        }
    }

    fun addDays(dateStr: String, days: Int): String {
        return try {
            val date = getFormatter().parse(dateStr) ?: return dateStr
            val cal = getUtcCalendar()
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, days)
            getFormatter().format(cal.time)
        } catch (_: Exception) {
            dateStr
        }
    }

    fun daysBetween(startDateStr: String, endDateStr: String): Long {
        return try {
            val start = getFormatter().parse(startDateStr) ?: return 0L
            val end = getFormatter().parse(endDateStr) ?: return 0L
            val diffMs = end.time - start.time
            diffMs / (1000L * 60L * 60L * 24L)
        } catch (_: Exception) {
            0L
        }
    }

    fun formatMonthYearLabel(dateStr: String): String {
        return try {
            val date = getFormatter().parse(dateStr) ?: return dateStr
            val sdf = SimpleDateFormat("MMM yyyy", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(date)
        } catch (_: Exception) {
            dateStr
        }
    }

    fun formatDayOfWeekShort(dateStr: String): String {
        return try {
            val date = getFormatter().parse(dateStr) ?: return "Day"
            val sdf = SimpleDateFormat("EEE", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(date)
        } catch (_: Exception) {
            "Day"
        }
    }

    fun extractDayOfMonth(dateStr: String): String {
        return dateStr.takeLast(2).trimStart('0').ifBlank { "1" }
    }

    /**
     * Resolves the active schedule for [targetDateStr] using exact date match first,
     * then offset from [missionStartDate], and finally Day 1 fallback.
     * Ensures Home ("Today's Plan") and 80-Day Schedule resolve the exact same schedule record.
     */
    fun resolveScheduleForDate(
        schedules: List<DailyScheduleEntity>,
        targetDateStr: String,
        missionStartDate: String = "2026-10-23"
    ): DailyScheduleEntity? {
        if (schedules.isEmpty()) return null
        val exactMatch = schedules.find { it.date == targetDateStr }
        if (exactMatch != null) return exactMatch

        val dayOffset = daysBetween(missionStartDate, targetDateStr).toInt() + 1
        if (dayOffset in 1..schedules.size) {
            schedules.find { it.dayNumber == dayOffset }?.let { return it }
        }
        return schedules.firstOrNull()
    }

    fun generate80DayDates(startDateStr: String = "2026-10-23", endDateStr: String = "2027-01-10"): List<Pair<String, Boolean>> {
        val result = mutableListOf<Pair<String, Boolean>>()
        try {
            val start = getFormatter().parse(startDateStr) ?: return emptyList()
            val end = getFormatter().parse(endDateStr) ?: return emptyList()
            val cal = getUtcCalendar()
            cal.time = start

            while (!cal.time.after(end)) {
                val formatted = getFormatter().format(cal.time)
                val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                result.add(Pair(formatted, isSunday))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (_: Exception) {
            // Fallback
        }
        return result
    }
}
